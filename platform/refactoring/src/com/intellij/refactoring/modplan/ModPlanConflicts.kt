// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.modcommand.ModShowConflicts
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.createSmartFileRange
import com.intellij.psi.findElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.impl.UnknownUsagesInUnloadedModules
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Unmodifiable
import java.util.Collections

/**
 * Conflicts found by a ModPlan refactoring, with the places they are at: a client reads the file and the range of a
 * [SmartPsiFileRange] and needs no PSI. A conflict of an element without a file, such as a directory, has no place.
 *
 * A fixable or a broken conflict has the [ModShowConflicts] semantics: a person cancels or goes on. A provider that reports a
 * fixable conflict must put its fix into its edits; this is not checked. A refactoring that cannot run is refused with a
 * [ModPlan.RefusedPlan], it is not a conflict.
 *
 * One location has one conflict. The conflicts of one location merge: the messages join, and the strongest kind stays.
 *
 * @param conflicts the conflicts as they are
 */
class ModPlanConflicts(conflicts: List<ModPlanConflict>) {
  val conflicts: @Unmodifiable List<ModPlanConflict> = conflicts.toList()

  /** @return true if a person has to agree before the refactoring runs: there is a fixable or a broken conflict */
  fun needsConfirmation(): Boolean = conflicts.any { it.kind != ModPlanConflictKind.NON_CODE_USAGE }

  /** @return true if there are conflicts that stay after the refactoring */
  fun hasBroken(): Boolean = conflicts.any { it.kind == ModPlanConflictKind.BROKEN }

  /** @return the places that the refactoring found and does not change */
  fun nonCodeUsages(): @Unmodifiable List<ModPlanConflict> = conflicts.filter { it.kind == ModPlanConflictKind.NON_CODE_USAGE }

  /** @return the conflicts of both; the conflicts of the same location are joined */
  fun merge(otherConflicts: ModPlanConflicts): ModPlanConflicts = ModPlanConflicts(mergedByLocation(conflicts + otherConflicts.conflicts))

  /**
   * @param anchor the element for a conflict without a location, or with a location where no element is found, see
   *               [com.intellij.psi.findElement]
   * @return the messages per element, without the non-code usages; the argument for [com.intellij.modcommand.ModCommand.showConflicts]
   */
  @RequiresReadLock
  fun toShowConflicts(anchor: PsiElement): @Unmodifiable Map<PsiElement, ModShowConflicts.Conflict> = toShowConflicts(anchor, false)

  /**
   * @return the messages per element, with the non-code usages, so that a person sees them before the command writes, see
   * [toShowConflicts]
   */
  @RequiresReadLock
  fun toShowConflictsWithNonCodeUsages(anchor: PsiElement): @Unmodifiable Map<PsiElement, ModShowConflicts.Conflict> {
    return toShowConflicts(anchor, true)
  }

  private fun toShowConflicts(anchor: PsiElement, withNonCodeUsages: Boolean): @Unmodifiable Map<PsiElement, ModShowConflicts.Conflict> {
    val resultConflicts = LinkedHashMap<PsiElement, ModShowConflicts.Conflict>()
    for (conflict in conflicts.sortedBy { it.location?.range?.startOffset ?: Int.MAX_VALUE }) {
      if (!withNonCodeUsages && conflict.kind == ModPlanConflictKind.NON_CODE_USAGE) continue
      val element = conflict.location?.findElement()
      resultConflicts.merge(element ?: anchor, ModShowConflicts.Conflict(conflict.messages), ModShowConflicts.Conflict::merge)
    }
    return Collections.unmodifiableMap(resultConflicts)
  }

  override fun equals(other: Any?): Boolean = this === other || other is ModPlanConflicts && conflicts == other.conflicts

  override fun hashCode(): Int = conflicts.hashCode()

  /** Builds the conflicts in the read action of the analysis, which makes the locations from the elements. */
  class Builder internal constructor() {
    private val myConflicts = ArrayList<ModPlanConflict>()

    /**
     * Adds a conflict that the command of the same analysis fixes.
     *
     * @param element the place; null for a conflict of the whole refactoring
     */
    @RequiresReadLock
    fun fixable(element: PsiElement?, message: @Nls String): Builder = add(element, message, ModPlanConflictKind.FIXABLE)

    /**
     * Adds a conflict that stays after the refactoring.
     *
     * @param element the place; null for a conflict of the whole refactoring
     */
    @RequiresReadLock
    fun broken(element: PsiElement?, message: @Nls String): Builder = add(element, message, ModPlanConflictKind.BROKEN)

    /**
     * Adds a place that the refactoring does not change, such as a non-code usage in a comment, a string or a non-code file.
     *
     * @param element        the element that holds the non-code usage
     * @param rangeInElement the range of the non-code usage inside [element]; null for the whole element
     * @param message        what a person is told about the non-code usage
     */
    @RequiresReadLock
    fun nonCodeUsage(element: PsiElement, rangeInElement: TextRange?, message: @Nls String): Builder {
      myConflicts.add(ModPlanConflict(element.createSmartFileRange(rangeInElement), listOf(message), ModPlanConflictKind.NON_CODE_USAGE))
      return this
    }

    /**
     * Adds a broken conflict of the whole refactoring if the use scope of an element holds modules that are not loaded: the usages in
     * them are unknown. It is the check of `BaseRefactoringProcessor`, and the text of the node that the legacy preview shows.
     */
    @RequiresReadLock
    fun unloadedModules(elements: Collection<PsiElement>): Builder {
      if (elements.isEmpty()) return this
      val project = elements.first().project
      if (ModuleManager.getInstance(project).unloadedModuleDescriptions.isEmpty()) return this
      val unloadedModules = LinkedHashSet<UnloadedModuleDescription>()
      for (element in elements) {
        val scope = element.useScope
        if (scope is GlobalSearchScope) {
          unloadedModules.addAll(scope.unloadedModulesBelongingToScope)
        }
      }
      if (unloadedModules.isNotEmpty()) {
        val message = UnknownUsagesInUnloadedModules(unloadedModules).presentation.plainText
        myConflicts.add(ModPlanConflict(null, listOf(message), ModPlanConflictKind.BROKEN))
      }
      return this
    }

    fun build(): ModPlanConflicts = ModPlanConflicts(mergedByLocation(myConflicts))

    private fun add(element: PsiElement?, message: @Nls String, kind: ModPlanConflictKind): Builder {
      myConflicts.add(ModPlanConflict(element?.createSmartFileRange(), listOf(message), kind))
      return this
    }
  }

  companion object {
    @JvmField
    val EMPTY: ModPlanConflicts = ModPlanConflicts(listOf())

    @JvmStatic
    fun builder(): Builder = Builder()

    private fun mergedByLocation(conflicts: Collection<ModPlanConflict>): List<ModPlanConflict> {
      val resultConflicts = LinkedHashMap<SmartPsiFileRange?, ModPlanConflict>()
      for (conflict in conflicts) {
        resultConflicts.merge(conflict.location, conflict) { conflict, otherConflict ->
          ModPlanConflict(conflict.location,
                          (conflict.messages + otherConflict.messages).distinct<@Nls String>(),
                          maxOf(conflict.kind, otherConflict.kind))
        }
      }
      return ArrayList(resultConflicts.values)
    }
  }
}

/**
 * A conflict of a ModPlan refactoring: the messages that [ModShowConflicts] shows for a place, and what agreeing to it does.
 *
 * @param location the place of the conflict; null for a conflict of the whole refactoring
 * @param messages what a person is told; a [ModPlanConflictKind.NON_CODE_USAGE] has its message too
 * @param kind     what happens to the conflict if a person goes on
 */
class ModPlanConflict(val location: SmartPsiFileRange?,
                      messages: List<@Nls String>,
                      val kind: ModPlanConflictKind) {
  val messages: @Unmodifiable List<@Nls String> = messages.toList()

  override fun equals(other: Any?): Boolean {
    return this === other || other is ModPlanConflict && location == other.location && messages == other.messages && kind == other.kind
  }

  override fun hashCode(): Int = (location.hashCode() * 31 + messages.hashCode()) * 31 + kind.hashCode()
}

/** In the order of strength: a merged conflict has the strongest kind. */
enum class ModPlanConflictKind {
  /** A place in a comment, a string or a non-code file that is not changed. Nothing breaks. */
  NON_CODE_USAGE,

  /** The command of the same analysis fixes it. */
  FIXABLE,

  /** It stays after the refactoring. */
  BROKEN
}
