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
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractPsiElements(nodes: List<BackendProjectViewNodeModel<T>>): List<PsiElement>

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractPsiDirectories(nodes: List<BackendProjectViewNodeModel<T>>): List<PsiDirectory>

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractProject(node: BackendProjectViewNodeModel<T>): Project?

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractSingleModule(node: BackendProjectViewNodeModel<T>): Module?

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractModules(nodes: List<BackendProjectViewNodeModel<T>>): List<Module>

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractUnloadedModules(nodes: List<BackendProjectViewNodeModel<T>>): List<UnloadedModuleDescription>

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractModuleGroups(nodes: List<BackendProjectViewNodeModel<T>>): List<ModuleGroup>

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractLibraryGroups(nodes: List<BackendProjectViewNodeModel<T>>): List<LibraryGroupElement>

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractNamedLibraryElements(nodes: List<BackendProjectViewNodeModel<T>>): List<NamedLibraryElement>
}
