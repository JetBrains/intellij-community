// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewPsiExtractor<T> {
  @RequiresReadLock
  fun extractPsiElements(nodes: List<BackendProjectViewNodeModel<T>>): List<PsiElement>

  @RequiresReadLock
  fun extractPsiDirectories(nodes: List<BackendProjectViewNodeModel<T>>): List<PsiDirectory>

  @RequiresReadLock
  fun extractProject(node: BackendProjectViewNodeModel<T>): Project?

  @RequiresReadLock
  fun extractSingleModule(node: BackendProjectViewNodeModel<T>): Module?

  @RequiresReadLock
  fun extractModules(nodes: List<BackendProjectViewNodeModel<T>>): List<Module>

  @RequiresReadLock
  fun extractUnloadedModules(nodes: List<BackendProjectViewNodeModel<T>>): List<UnloadedModuleDescription>

  @RequiresReadLock
  fun extractModuleGroups(nodes: List<BackendProjectViewNodeModel<T>>): List<ModuleGroup>

  @RequiresReadLock
  fun extractLibraryGroups(nodes: List<BackendProjectViewNodeModel<T>>): List<LibraryGroupElement>

  @RequiresReadLock
  fun extractNamedLibraryElements(nodes: List<BackendProjectViewNodeModel<T>>): List<NamedLibraryElement>
}
