// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus

/**
 * Builds the scope list of one [SeTargetItemsProvider], and names the scope of each toggle.
 */
@ApiStatus.Experimental
interface SeTargetScopesProvider {
  /**
   * The raw scope list. The caller runs this in a read action.
   */
  fun createDescriptors(project: Project, psiContext: SmartPsiElementPointer<PsiElement?>?): List<ScopeDescriptor>

  /** The scope of the everywhere toggle, or null when the list holds none. */
  fun findEverywhereScope(project: Project, descriptors: List<ScopeDescriptor>): ScopeDescriptor?

  /**
   * The scope that a new session starts on, or null when the list holds none.
   *
   * The scope chooser can auto toggle to the everywhere scope only when this scope differs from
   * [everywhere]. See `SeScopeChooserActionProvider.canToggleEverywhere`.
   */
  fun findProjectScope(project: Project, descriptors: List<ScopeDescriptor>, everywhere: ScopeDescriptor?): ScopeDescriptor?

  companion object {
    /** The scope list that every Goto contributor shows. */
    val DEFAULT: SeTargetScopesProvider = SeDefaultTargetScopesProvider
  }
}

/**
 * The default [SeTargetScopesProvider]. It matches the two toggles by the platform display name.
 */
internal object SeDefaultTargetScopesProvider : SeTargetScopesProvider {
  override fun createDescriptors(project: Project, psiContext: SmartPsiElementPointer<PsiElement?>?): List<ScopeDescriptor> =
    AbstractGotoSEContributor.createScopes(project, psiContext)

  override fun findEverywhereScope(project: Project, descriptors: List<ScopeDescriptor>): ScopeDescriptor? =
    descriptors.withDisplayName(GlobalSearchScope.everythingScope(project).displayName)

  override fun findProjectScope(
    project: Project,
    descriptors: List<ScopeDescriptor>,
    everywhere: ScopeDescriptor?,
  ): ScopeDescriptor? =
    descriptors.withDisplayName(GlobalSearchScope.projectScope(project).displayName)

  private fun List<ScopeDescriptor>.withDisplayName(name: String): ScopeDescriptor? =
    firstOrNull { it.displayName == name }
}
