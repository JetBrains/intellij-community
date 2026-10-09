// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.SmartList
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

/**
 * Base interface for collections of file sets associated with a file in [WorkspaceFileIndexData]. 
 * The elements in the collection are of two kinds: [WorkspaceFileSetImpl] for actual file sets added to the index, and [ExcludedFileSet] 
 * to describe files excluded from the index.
 * 
 * It's essential that elements implementing [ExcludedFileSet] always come before [WorkspaceFileSetImpl] in the collection and processed 
 * first by [computeMasks] and [forEach] functions.
 * 
 * The collections are not thread safe.
 */
internal sealed interface StoredFileSetCollection {
  /** Adds a new element to the collection and return the updated set. */
  fun add(fileSet: StoredFileSet): StoredFileSetCollection

  /**
   * Adds all [fileSets] at once and returns the updated collection. Prefer this to a loop over [add], which rebuilds
   * the backing list once per element.
   *
   * Like [add], this appends within each of the two groups, so elements stay in registration order. [find] returns
   * the first match, so the order decides which file set wins for a root that has several.
   */
  fun addAll(fileSets: List<StoredFileSet>): StoredFileSetCollection {
    if (fileSets.isEmpty()) return this
    if (fileSets.size == 1) return add(fileSets[0])
    val combined = ArrayList<StoredFileSet>(fileSets.size + 2)
    forEach { if (it is ExcludedFileSet) combined.add(it) }
    fileSets.filterTo(combined) { it is ExcludedFileSet }
    forEach { if (it !is ExcludedFileSet) combined.add(it) }
    fileSets.filterTo(combined) { it !is ExcludedFileSet }
    return MultipleStoredWorkspaceFileSets(combined)
  }

  /**
   * Removes elements satisfying the given predicate and returns the update collection if `null` if all elements were removed.
   * [predicate] is called exactly once per element, so a caller may collect the removed elements from it.
   */
  fun removeIf(predicate: (StoredFileSet) -> Boolean): StoredFileSetCollection?

  /**
   * Updates `currentMasks` accordingly to elements stored in the collection and returns the updated masks. Two masks are stored in these 
   * integers:
   * * lower bits stores [StoredFileSetKindMask] describing which kinds of elements are stored in this collection;
   * * higher bits stores [WorkspaceFileKindMask] describing which kinds of file sets can be accepted (i.e. they were enabled in 
   * [WorkspaceFileIndexEx.getFileInfo] call and weren't excluded).
   * @param honorExclusion whether [ExcludedFileSet] should be taken into account
   * @param file actual file passed to [WorkspaceFileIndexEx.getFileInfo]
   */
  fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int

  /**
   * Applies given [action] to all elements in the collection.
   */
  fun forEach(action: (StoredFileSet) -> Unit)
}

/**
 * Size of shift for [WorkspaceFileKindMask] bits in [StoredFileSetCollection.computeMasks] data.
 */
internal const val ACCEPTED_KINDS_MASK_SHIFT = 2

/**
 * Represents kinds of elements stored in [StoredFileSetCollection].
 */
internal object StoredFileSetKindMask {
  /** 
   * Indicated an [WorkspaceFileSetImpl] instance, which is not excluded or unloaded and therefore should be returned from 
   * [WorkspaceFileIndexEx.getFileInfo] call. 
   */
  const val ACCEPTED_FILE_SET = 1

  /**
   * Indicates an element, which is either excluded or unloaded and therefore won't be returned from 
   * [WorkspaceFileIndexEx.getFileInfo] call. 
   */
  const val IRRELEVANT_FILE_SET = 2
  
  const val ALL = ACCEPTED_FILE_SET or IRRELEVANT_FILE_SET
}

/**
 * Represents a single fileset or a set of excluded files. Since in most of the cases a file is associated with a single fileset, 
 * this interface implements [StoredFileSetCollection] to optimize performance and memory usage.
 */
internal sealed interface StoredFileSet : StoredFileSetCollection {
  val entityPointer: EntityPointer<WorkspaceEntity>
  val entityStorageKind: EntityStorageKind
  
  override fun removeIf(predicate: (StoredFileSet) -> Boolean): StoredFileSetCollection? {
    return if (predicate(this)) null else this
  }

  override fun forEach(action: (StoredFileSet) -> Unit) {
    action(this)
  }

  /**
   * The method compares every property of [StoredFileSet] except for associated [WorkspaceEntity].
   * For the associated [WorkspaceEntity] it compares class.
   */
  fun hasSameProperties(other: StoredFileSet): Boolean

  /**
   * The method returns [Any.hashCode] for every property of [StoredFileSet] except for associated [WorkspaceEntity].
   * For the associated [WorkspaceEntity] it computes hashcode for its class.
   */
  fun hashcodeOfProperties(): Int

  abstract override fun toString(): String
}

/**
 * Represents an actual set of files registered in [WorkspaceFileIndexData].
 */
internal class WorkspaceFileSetImpl(
  override val root: VirtualFile,
  override val kind: WorkspaceFileKind,
  override val entityPointer: EntityPointer<WorkspaceEntity>,
  override val entityStorageKind: EntityStorageKind,
  override val data: WorkspaceFileSetData,
  override val recursive: Boolean = true,
) : WorkspaceFileSetWithCustomData<WorkspaceFileSetData>, StoredFileSet, WorkspaceFileInternalInfo {

  /**
   * The [RootDescriptor] this file set resolves to, computed on first demand - see `FileSet2RootDescriptor.kt`.
   *
   * The slot may be filled but never invalidated: when the entity behind a file set changes, the index discards the
   * whole file set and stores a fresh instance (see [WorkspaceFileIndexDataImpl.onEntitiesChanged] and
   * [WorkspaceFileIndexDataImpl.updateDirtyEntities]), and the module, library or SDK bridge the descriptor holds is
   * itself stable per entity. The race on the field is benign - two threads can only store equal descriptors.
   */
  @Volatile
  internal var cachedRootDescriptor: RootDescriptor? = null

  override val fileSets: List<WorkspaceFileSetWithCustomData<*>> get() = listOf(this)

  override fun add(fileSet: StoredFileSet): StoredFileSetCollection {
    return if (fileSet is WorkspaceFileSetImpl) TwoWorkspaceFileSets(this, fileSet) else MultipleStoredWorkspaceFileSets(listOf(fileSet, this))
  }

  override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
    val acceptedKindMask = (currentMasks shr ACCEPTED_KINDS_MASK_SHIFT) and WorkspaceFileKindMask.ALL
    val update = if (accepts(acceptedKindMask, file)) {
      StoredFileSetKindMask.ACCEPTED_FILE_SET
    }
    else {
      StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }
    return currentMasks or update
  }

  fun accepts(acceptedKindMask: Int, file: VirtualFile?): Boolean {
    return acceptedKindMask and kind.toMask() != 0 && (recursive || root == file)
  }

  override fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>? {
    return this.takeIf(condition)
  }

  override fun findFileSets(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): List<WorkspaceFileSetWithCustomData<*>> {
    return listOfNotNull(findFileSet(condition))
  }

  override fun hasSameProperties(other: StoredFileSet): Boolean {
    if (other !is WorkspaceFileSetImpl) return false
    return root == other.root &&
           kind == other.kind &&
           data == other.data &&
           recursive == other.recursive &&
           entityStorageKind == other.entityStorageKind &&
           entityPointer.isPointerToEntityOfSameTypeAs(other.entityPointer)
  }

  override fun hashcodeOfProperties(): Int {
    var result = recursive.hashCode()
    result = 31 * result + root.hashCode()
    result = 31 * result + kind.hashCode()
    result = 31 * result + entityPointer.classHashcode()
    result = 31 * result + entityStorageKind.hashCode()
    result = 31 * result + data.hashCode()
    return result
  }

  override fun toString(): String {
    return "WorkspaceFileSet{root=$root, kind=$kind}"
  }
}

/**
 * This class is introduced to optimize performance and memory usage in a common case when two [WorkspaceFileSetImpl]'s are associated with 
 * the same root (e.g., a content root and a source root). 
 */
private data class TwoWorkspaceFileSets(private val first: WorkspaceFileSetImpl, private val second: WorkspaceFileSetImpl): StoredFileSetCollection, MultipleWorkspaceFileSets {
  override fun add(fileSet: StoredFileSet): StoredFileSetCollection {
    val all: List<StoredFileSet> =
      if (fileSet is ExcludedFileSet) listOf(fileSet, first, second) else listOf(first, second, fileSet)
    return MultipleStoredWorkspaceFileSets(all)
  }

  override fun removeIf(predicate: (StoredFileSet) -> Boolean): StoredFileSetCollection? {
    val removeFirst = predicate(first)
    val removeSecond = predicate(second)
    return when {
      removeFirst && removeSecond -> null
      removeFirst -> second
      removeSecond -> first
      else -> this
    }
  }

  override fun forEach(action: (StoredFileSet) -> Unit) {
    action(first)
    action(second)
  }

  override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
    val afterFirst = first.computeMasks(currentMasks, project, honorExclusion, file)
    return second.computeMasks(afterFirst, project, honorExclusion, file)
  }

  override val fileSets: List<WorkspaceFileSetImpl>
    get() = listOf(first, second)

  override fun find(acceptedCustomDataClass: Class<out WorkspaceFileSetData>?): WorkspaceFileSetImpl? {
    return when {
      acceptedCustomDataClass == null || acceptedCustomDataClass.isInstance(first.data) -> first
      acceptedCustomDataClass.isInstance(second.data) -> second
      else -> null
    }
  }

  override fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>? {
    return first.takeIf(condition) ?: second.takeIf(condition)
  }

  override fun findFileSets(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): List<WorkspaceFileSetWithCustomData<*>> {
    val firstChecked = first.takeIf(condition)
    val secondChecked = second.takeIf(condition)

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    return when {
      firstChecked != null && secondChecked != null -> java.util.List.of(firstChecked, secondChecked)
      firstChecked != null -> listOf(firstChecked)
      secondChecked != null -> listOf(secondChecked)
      else -> emptyList()
    }
  }

  override fun toString(): String {
    return "TwoWorkspaceFileSets{$first, $second}"
  }
}

/**
 * Represents a generic case with multiple elements in [StoredFileSetCollection].
 */
internal class MultipleStoredWorkspaceFileSets(private val storedFileSets: List<StoredFileSet>) : StoredFileSetCollection, MultipleWorkspaceFileSets {
  /**
   * Returns a new list with [added] merged in, keeping every [ExcludedFileSet] before every [WorkspaceFileSetImpl]
   * and appending within each of the two groups.
   */
  private fun merged(added: List<StoredFileSet>): List<StoredFileSet> {
    val excluded = added.filterIsInstance<ExcludedFileSet>()
    if (excluded.isEmpty()) return storedFileSets + added
    val boundary = storedFileSets.indexOfFirst { it !is ExcludedFileSet }.let { if (it < 0) storedFileSets.size else it }
    val combined = ArrayList<StoredFileSet>(storedFileSets.size + added.size)
    combined.addAll(storedFileSets.subList(0, boundary))
    combined.addAll(excluded)
    combined.addAll(storedFileSets.subList(boundary, storedFileSets.size))
    added.filterTo(combined) { it !is ExcludedFileSet }
    return combined
  }

  override fun add(fileSet: StoredFileSet): StoredFileSetCollection {
    return MultipleStoredWorkspaceFileSets(merged(listOf(fileSet)))
  }

  override fun addAll(fileSets: List<StoredFileSet>): StoredFileSetCollection {
    if (fileSets.isEmpty()) return this
    return MultipleStoredWorkspaceFileSets(merged(fileSets))
  }

  override fun removeIf(predicate: (StoredFileSet) -> Boolean): StoredFileSetCollection? {
    val remaining = storedFileSets.filterNot(predicate)
    return when (remaining.size) {
      0 -> null
      1 -> remaining.single()
      2 -> {
        val (first, second) = remaining
        if (first is WorkspaceFileSetImpl && second is WorkspaceFileSetImpl) TwoWorkspaceFileSets(first, second) else MultipleStoredWorkspaceFileSets(remaining)
      }
      else -> MultipleStoredWorkspaceFileSets(remaining)
    }
  }

  override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
    var result = currentMasks
    for (fileSet in storedFileSets) {
      result = fileSet.computeMasks(result, project, honorExclusion, file)
    }
    return result 
  }

  override fun forEach(action: (StoredFileSet) -> Unit) {
    for (fileSet in storedFileSets) {
      action(fileSet)
    }
  }

  @Suppress("UNCHECKED_CAST")
  override val fileSets: List<WorkspaceFileSetImpl>
    get() = storedFileSets as List<WorkspaceFileSetImpl>

  override fun find(acceptedCustomDataClass: Class<out WorkspaceFileSetData>?): WorkspaceFileSetImpl? {
    return storedFileSets.find {
      it is WorkspaceFileSetImpl && (acceptedCustomDataClass == null || acceptedCustomDataClass.isInstance(it.data))
    } as? WorkspaceFileSetImpl
  }

  override fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>? {
    return storedFileSets.find { 
      it is WorkspaceFileSetImpl && condition(it)
    } as? WorkspaceFileSetWithCustomData<*>
  }

  override fun findFileSets(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): List<WorkspaceFileSetWithCustomData<*>> {
    @Suppress("UNCHECKED_CAST")
    return storedFileSets.filter {
      it is WorkspaceFileSetImpl && condition(it)
    } as List<WorkspaceFileSetWithCustomData<*>>
  }

  override fun toString(): String {
    return "MultipleStoredWorkspaceFileSets{${storedFileSets.joinToString()}}"
  }
}

internal class MultipleWorkspaceFileSetsImpl(override val fileSets: List<WorkspaceFileSetImpl>) : MultipleWorkspaceFileSets {
  override fun find(acceptedCustomDataClass: Class<out WorkspaceFileSetData>?): WorkspaceFileSetImpl? {
    return fileSets.find { acceptedCustomDataClass == null || acceptedCustomDataClass.isInstance(it.data) }
  }

  override fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>? {
    return fileSets.find(condition)
  }

  override fun toString(): String {
    return "MultipleWorkspaceFileSets{${fileSets.joinToString()}}"
  }

  override fun findFileSets(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): List<WorkspaceFileSetWithCustomData<*>> {
    return fileSets.filter(condition)
  }
}

internal object DummyWorkspaceFileSetData : WorkspaceFileSetData

/**
 * Bit mask representing a set of items from [WorkspaceFileKind].
 */
internal object WorkspaceFileKindMask {
  const val CONTENT = 1
  const val EXTERNAL_BINARY = 2
  const val EXTERNAL_SOURCE = 4
  const val EXTERNAL = EXTERNAL_SOURCE or EXTERNAL_BINARY
  const val CUSTOM = 8
  const val CONTENT_NON_INDEXABLE = 16
  const val EXTERNAL_NON_INDEXABLE = 32
  const val ALL = CONTENT or EXTERNAL or CUSTOM or CONTENT_NON_INDEXABLE or EXTERNAL_NON_INDEXABLE
}

/**
 * Defines an exclusion rule registered at [root].
 * A [WorkspaceFileSetImpl] defines an inclusion rule that adds files to the workspace.
 * Exclusion rules restrict which files belong to these file sets.
 *
 * Inclusion rules can be registered below [root], inside the scope of an exclusion rule.
 * The exclusion type determines whether it also applies to files covered by a nested inclusion rule:
 *
 * * [ByFileKind], [DirectoryRoot], [ByPattern], and [ByCondition] allow a nested inclusion rule to include excluded files again.
 * * [ByUnscopedCondition] continues to apply inside a nested inclusion rule. There it checks only the path from the file up to the root
 *   of that rule.
 *
 * [WorkspaceFileIndexEx.getFileInfo] applies exclusion rules when `honorExclusion` is `true`.
 */
internal sealed interface ExcludedFileSet : StoredFileSet, WorkspaceExcludeFileSet {
  /**
   * The file or directory where the rule is registered.
   * The rule type determines whether the rule excludes this root itself.
   */
  val root: VirtualFile

  override fun add(fileSet: StoredFileSet): StoredFileSetCollection {
    return MultipleStoredWorkspaceFileSets(listOf(this, fileSet))
  }

  /**
   * Excludes [root] and its descendants from the kinds selected by [mask].
   * [mask] uses [WorkspaceFileKindMask] bits. [WorkspaceFileKindMask.ALL] excludes all kinds.
   * A nested inclusion rule can include files again.
   */
  class ByFileKind(override val root: VirtualFile, @MagicConstant(flagsFromClass = WorkspaceFileKindMask::class) override val mask: Int,
                   override val entityPointer: EntityPointer<WorkspaceEntity>,
                   override val entityStorageKind: EntityStorageKind = EntityStorageKind.MAIN)
    : ExcludedFileSet, WorkspaceExcludeFileSet.ByFileKind {
    override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
      val withExclusion = if (honorExclusion) currentMasks.unsetAcceptedKinds(mask) else currentMasks
      return withExclusion or StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }

    override fun hasSameProperties(other: StoredFileSet): Boolean {
      if (other !is ByFileKind) return false
      return root == other.root &&
             mask == other.mask &&
             entityStorageKind == other.entityStorageKind &&
             entityPointer.isPointerToEntityOfSameTypeAs(other.entityPointer)
    }

    override fun hashcodeOfProperties(): Int {
      var result = mask
      result = 31 * result + root.hashCode()
      result = 31 * result + entityPointer.classHashcode()
      result = 31 * result + entityStorageKind.hashCode()
      return result
    }

    override fun toString(): String {
      return "ExcludedFileSet.ByFileKind{mask=$mask}"
    }
  }

  /**
   * Excludes [root] and its descendants from all kinds while [root] is a directory. Such a [root] is left as it is while it is a file.
   * A nested inclusion rule can include files again.
   */
  class DirectoryRoot(override val root: VirtualFile,
                      override val entityPointer: EntityPointer<WorkspaceEntity>,
                      override val entityStorageKind: EntityStorageKind) : ExcludedFileSet, WorkspaceExcludeFileSet.DirectoryRoot {
    override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
      val withExclusion = if (honorExclusion && root.isDirectory) currentMasks.unsetAcceptedKinds(WorkspaceFileKindMask.ALL) else currentMasks
      return withExclusion or StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }

    override fun hasSameProperties(other: StoredFileSet): Boolean {
      if (other !is DirectoryRoot) return false
      return root == other.root &&
             entityStorageKind == other.entityStorageKind &&
             entityPointer.isPointerToEntityOfSameTypeAs(other.entityPointer)
    }

    override fun hashcodeOfProperties(): Int {
      var result = root.hashCode()
      result = 31 * result + entityPointer.classHashcode()
      result = 31 * result + entityStorageKind.hashCode()
      return result
    }

    override fun toString(): String {
      return "ExcludedFileSet.DirectoryRoot{root=$root}"
    }
  }

  /**
   * Excludes matching files and directories from all kinds.
   * The rule applies to [root] when `workspace.model.exclude.by.pattern.apply.to.root.enable` is enabled, and always to its descendants.
   * The [patterns] match file names and support `*` and `?` wildcards.
   * The rule also excludes the descendants of matching directories.
   * A nested inclusion rule can include files again.
   */
  class ByPattern(override val root: VirtualFile, patterns: List<String>,
                  override val entityPointer: EntityPointer<WorkspaceEntity>,
                  override val entityStorageKind: EntityStorageKind) : ExcludedFileSet, WorkspaceExcludeFileSet.ByPattern {
    val table = FileTypeAssocTableUtil.newScalableFileTypeAssocTable<Boolean>()

    init {
      for (pattern in patterns) {
        table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), true)
      }
    }

    override fun matches(fileName: CharSequence): Boolean = table.findAssociatedFileType(fileName) != null

    private fun isExcluded(file: VirtualFile): Boolean {
      var current = file
      while (current != root) {
        if (matches(current.nameSequence)) {
          return true
        }
        current = current.parent
      }
      return RegistryManager.getInstance().`is`("workspace.model.exclude.by.pattern.apply.to.root.enable", false)
             && matches(root.nameSequence)
    }

    override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
      val withExclusion = if (honorExclusion && isExcluded(file)) currentMasks.unsetAcceptedKinds(WorkspaceFileKindMask.ALL) else currentMasks
      return withExclusion or StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }

    override fun hasSameProperties(other: StoredFileSet): Boolean {
      if (other !is ByPattern) return false
      return root == other.root &&
             table == other.table &&
             entityStorageKind == other.entityStorageKind &&
             entityPointer.isPointerToEntityOfSameTypeAs(other.entityPointer)
    }

    override fun hashcodeOfProperties(): Int {
      var result = root.hashCode()
      result = 31 * result + entityPointer.classHashcode()
      result = 31 * result + entityStorageKind.hashCode()
      result = 31 * result + table.hashCode()
      return result
    }

    override fun toString(): String {
      return "ExcludedFileSet.ByPattern{root=$root, patterns=$table}"
    }
  }

  /**
   * Excludes files and directories that satisfy [condition] from all kinds.
   * The rule applies to [root] and its descendants.
   * It also excludes the descendants of matching directories.
   * A nested inclusion rule can include files again.
   */
  class ByCondition(override val root: VirtualFile, override val condition: WorkspaceFileSetExclusionCondition,
                    override val entityPointer: EntityPointer<WorkspaceEntity>,
                    override val entityStorageKind: EntityStorageKind) : ExcludedFileSet, WorkspaceExcludeFileSet.ByCondition {
    private fun isExcluded(file: VirtualFile): Boolean {
      var current = file
      while (current != root) {
        if (condition.shouldExclude(current)) {
          return true
        }
        current = current.parent
      }

      return condition.shouldExclude(root)
    }

    override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
      val withExclusion = if (honorExclusion && isExcluded(file)) currentMasks.unsetAcceptedKinds(WorkspaceFileKindMask.ALL) else currentMasks
      return withExclusion or StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }

    override fun hasSameProperties(other: StoredFileSet): Boolean {
      if (other !is ByCondition) return false
      return root == other.root &&
             condition == other.condition &&
             entityStorageKind == other.entityStorageKind &&
             entityPointer.isPointerToEntityOfSameTypeAs(other.entityPointer)
    }

    override fun hashcodeOfProperties(): Int {
      var result = root.hashCode()
      result = 31 * result + condition.hashCode()
      result = 31 * result + entityPointer.classHashcode()
      result = 31 * result + entityStorageKind.hashCode()
      return result
    }

    override fun toString(): String {
      return "ExcludedFileSet.ByCondition{root=$root}"
    }
  }

  /**
   * Uses the same condition check as [ByCondition].
   * The exclusion continues to apply inside a nested inclusion rule, but only to the path below the root of that rule.
   * See [excludesUpTo].
   */
  class ByUnscopedCondition(override val root: VirtualFile, override val condition: WorkspaceFileSetExclusionCondition,
                            override val entityPointer: EntityPointer<WorkspaceEntity>,
                            override val entityStorageKind: EntityStorageKind)
    : ExcludedFileSet, WorkspaceExcludeFileSet.ByUnscopedCondition {
    /**
     * Returns `true` if [condition] matches [file] or a parent of it up to [nestedRoot], with [nestedRoot] included.
     * [nestedRoot] is the root of the nearest inclusion rule of [file] below [root].
     * A match above [nestedRoot] does not count, so that inclusion rule includes the files again.
     */
    fun excludesUpTo(file: VirtualFile, nestedRoot: VirtualFile): Boolean {
      var current: VirtualFile? = file
      while (current != null) {
        if (condition.shouldExclude(current)) {
          return true
        }
        if (current == nestedRoot) {
          return false
        }
        current = current.parent
      }
      return false
    }

    private fun isExcluded(file: VirtualFile): Boolean {
      var current = file
      while (current != root) {
        if (condition.shouldExclude(current)) {
          return true
        }
        current = current.parent
      }

      return condition.shouldExclude(root)
    }

    override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
      val withExclusion = if (honorExclusion && isExcluded(file)) currentMasks.unsetAcceptedKinds(WorkspaceFileKindMask.ALL) else currentMasks
      return withExclusion or StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }

    override fun hasSameProperties(other: StoredFileSet): Boolean {
      if (other !is ByUnscopedCondition) return false
      return root == other.root &&
             condition == other.condition &&
             entityStorageKind == other.entityStorageKind &&
             entityPointer.isPointerToEntityOfSameTypeAs(other.entityPointer)
    }

    override fun hashcodeOfProperties(): Int {
      var result = root.hashCode()
      result = 31 * result + condition.hashCode()
      result = 31 * result + entityPointer.classHashcode()
      result = 31 * result + entityStorageKind.hashCode()
      return result
    }

    override fun toString(): String {
      return "ExcludedFileSet.ByUnscopedCondition{root=$root}"
    }
  }
}

private fun Int.unsetAcceptedKinds(excludedKinds: Int) = this and (excludedKinds shl ACCEPTED_KINDS_MASK_SHIFT).inv() 

internal fun <K : Any> MutableMap<K, StoredFileSetCollection>.putValue(key: K, fileSet: StoredFileSet) {
  merge(key, fileSet) { oldValue, newValue -> oldValue.add(newValue as StoredFileSet) }
}

internal fun <K> MutableMap<K, StoredFileSetCollection>.removeValueIf(key: K, valuePredicate: (StoredFileSet) -> Boolean) {
  val old = this[key] ?: return
  val updated = old.removeIf(valuePredicate)
  if (updated == null) {
    remove(key)
  }
  else if (updated !== old) {
    this[key] = updated
  }
}

internal typealias PackagePrefixStorage = HashMap<String, LinkedHashMap<EntityPointer<WorkspaceEntity>, MutableList<WorkspaceFileSetImpl>>>

internal fun PackagePrefixStorage.addFileSet(packagePrefix: String, fileSet: WorkspaceFileSetImpl) {
  val entityRef2FileSet = computeIfAbsent(packagePrefix) { LinkedHashMap() }
  entityRef2FileSet.computeIfAbsent(fileSet.entityPointer) { SmartList() }.add(fileSet)
}

internal fun PackagePrefixStorage.removeByPrefixAndPointer(packagePrefix: String, entityPointer: EntityPointer<WorkspaceEntity>) {
  val entityRef2FileSet = get(packagePrefix) ?: return
  entityRef2FileSet.remove(entityPointer)
  if (entityRef2FileSet.isEmpty()) {
    remove(packagePrefix)
  }
}