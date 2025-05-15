// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
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

  /** Removes elements satisfying the given predicate and returns the update collection if `null` if all elements were removed. */
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

  abstract override fun toString(): String
}

/**
 * Represents an actual set of files registered in [WorkspaceFileIndexData].
 */
internal class WorkspaceFileSetImpl(override val root: VirtualFile,
                                    override val kind: WorkspaceFileKind,
                                    override val entityPointer: EntityPointer<WorkspaceEntity>,
                                    override val entityStorageKind: EntityStorageKind,
                                    override val data: WorkspaceFileSetData,
                                    override val recursive: Boolean = true)
  : WorkspaceFileSetWithCustomData<WorkspaceFileSetData>, StoredFileSet, WorkspaceFileInternalInfo {

  override val fileSets: List<WorkspaceFileSetWithCustomData<*>> get() = listOf(this)

  fun isUnloaded(project: Project): Boolean {
    return (data as? UnloadableFileSetData)?.isUnloaded(project) == true
  }

  override fun add(fileSet: StoredFileSet): StoredFileSetCollection {
    return if (fileSet is WorkspaceFileSetImpl) TwoWorkspaceFileSets(this, fileSet) else MultipleStoredWorkspaceFileSets(mutableListOf(fileSet, this))
  }

  override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
    val acceptedKindMask = (currentMasks shr ACCEPTED_KINDS_MASK_SHIFT) and WorkspaceFileKindMask.ALL
    val update = if (acceptedKindMask and kind.toMask() != 0 && !isUnloaded(project) && (recursive || root == file)) {
      StoredFileSetKindMask.ACCEPTED_FILE_SET
    }
    else {
      StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }
    return currentMasks or update
  }

  override fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>? {
    return this.takeIf(condition)
  }

  override fun findFileSets(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): List<WorkspaceFileSetWithCustomData<*>> {
    return listOfNotNull(findFileSet(condition))
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
    return MultipleStoredWorkspaceFileSets(mutableListOf(fileSet, first, second))
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
internal class MultipleStoredWorkspaceFileSets(private val storedFileSets: MutableList<StoredFileSet>) : StoredFileSetCollection, MultipleWorkspaceFileSets {
  override fun add(fileSet: StoredFileSet): StoredFileSetCollection {
    if (fileSet is ExcludedFileSet && storedFileSets.last() !is ExcludedFileSet) {
      storedFileSets.add(0, fileSet)
    }
    else {
      storedFileSets.add(fileSet)
    }
    return this
  }

  override fun removeIf(predicate: (StoredFileSet) -> Boolean): StoredFileSetCollection? {
    storedFileSets.removeIf(predicate)
    return when (storedFileSets.size) {
      0 -> null
      1 -> storedFileSets.single()
      2 -> {
        val (first, second) = storedFileSets
        if (first is WorkspaceFileSetImpl && second is WorkspaceFileSetImpl) TwoWorkspaceFileSets(first, second) else this
      }
      else -> this
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

internal class MultipleWorkspaceFileSetsImpl(override val fileSets: List<WorkspaceFileSetImpl>): MultipleWorkspaceFileSets {
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
  const val ALL = CONTENT or EXTERNAL or CUSTOM or CONTENT_NON_INDEXABLE
}

internal sealed interface ExcludedFileSet : StoredFileSet {
  override fun add(fileSet: StoredFileSet): StoredFileSetCollection {
    return MultipleStoredWorkspaceFileSets(mutableListOf(this, fileSet))
  }

  class ByFileKind(@MagicConstant(flagsFromClass = WorkspaceFileKindMask::class) val mask: Int,
                   override val entityPointer: EntityPointer<WorkspaceEntity>,
                   override val entityStorageKind: EntityStorageKind = EntityStorageKind.MAIN) : ExcludedFileSet {
    override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
      val withExclusion = if (honorExclusion) currentMasks.unsetAcceptedKinds(mask) else currentMasks
      return withExclusion or StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }

    override fun toString(): String {
      return "ExcludedFileSet.ByFileKind{mask=$mask}"
    }
  }

  class ByPattern(val root: VirtualFile, patterns: List<String>,
                  override val entityPointer: EntityPointer<WorkspaceEntity>,
                  override val entityStorageKind: EntityStorageKind) : ExcludedFileSet {
    val table = FileTypeAssocTable<Boolean>()

    init {
      for (pattern in patterns) {
        table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), true)
      }
    }

    private fun isExcluded(file: VirtualFile): Boolean {
      var current = file
      while (current != root) {
        if (table.findAssociatedFileType(current.nameSequence) != null) {
          return true
        }
        current = current.parent
      }
      return false
    }

    override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
      val withExclusion = if (honorExclusion && isExcluded(file)) currentMasks.unsetAcceptedKinds(WorkspaceFileKindMask.ALL) else currentMasks
      return withExclusion or StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }

    override fun toString(): String {
      return "ExcludedFileSet.ByPattern{root=$root, patterns=$table}"
    }
  }

  class ByCondition(val root: VirtualFile, val condition: (VirtualFile) -> Boolean,
                    override val entityPointer: EntityPointer<WorkspaceEntity>,
                    override val entityStorageKind: EntityStorageKind) : ExcludedFileSet {
    private fun isExcluded(file: VirtualFile): Boolean {
      var current = file
      while (current != root) {
        if (condition(current)) {
          return true
        }
        current = current.parent
      }

      return condition(root)
    }

    override fun computeMasks(currentMasks: Int, project: Project, honorExclusion: Boolean, file: VirtualFile): Int {
      val withExclusion = if (honorExclusion && isExcluded(file)) currentMasks.unsetAcceptedKinds(WorkspaceFileKindMask.ALL) else currentMasks
      return withExclusion or StoredFileSetKindMask.IRRELEVANT_FILE_SET
    }

    override fun toString(): String {
      return "ExcludedFileSet.ByCondition{root=$root}"
    }
  }
}

private fun Int.unsetAcceptedKinds(excludedKinds: Int) = this and (excludedKinds shl ACCEPTED_KINDS_MASK_SHIFT).inv() 

internal fun <K> MutableMap<K, StoredFileSetCollection>.putValue(key: K, fileSet: StoredFileSet) {
  this[key] = this[key]?.add(fileSet) ?: fileSet
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

internal typealias PackagePrefixStorage = HashMap<String, MultiMap<EntityPointer<WorkspaceEntity>, WorkspaceFileSetImpl>>

internal fun PackagePrefixStorage.addFileSet(packagePrefix: String, fileSet: WorkspaceFileSetImpl) {
  val entityRef2FileSet = getOrPut(packagePrefix) { MultiMap(LinkedHashMap()) }
  entityRef2FileSet.putValue(fileSet.entityPointer, fileSet)
}

internal fun PackagePrefixStorage.removeByPrefixAndPointer(packagePrefix: String, entityPointer: EntityPointer<WorkspaceEntity>) {
  val entityRef2FileSet = get(packagePrefix) ?: return
  entityRef2FileSet.remove(entityPointer)
  if (entityRef2FileSet.isEmpty) {
    remove(packagePrefix)
  }
}