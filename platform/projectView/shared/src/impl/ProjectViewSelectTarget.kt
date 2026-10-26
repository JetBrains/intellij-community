// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.projectView.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.ApiStatus

/**
 * The thing that a Select In request targets in a [TreeBasedProjectViewPaneModel].
 *
 * [TreeBasedProjectViewPaneModel.computeSelectTarget] creates it, and [TreeBasedProjectViewPaneModel.findNodeForSelectTarget] finds its node.
 * A pane that overrides both can use its own kind of target.
 */
@ApiStatus.Experimental
interface ProjectViewSelectTarget

/**
 * The default target: a PSI element, its file, or both.
 * The search tries the element first, then the file only.
 *
 * Use [createProjectViewPsiSelectTarget] to create one.
 */
@ApiStatus.Experimental
interface ProjectViewPsiSelectTarget : ProjectViewSelectTarget {
  val elementPointer: SmartPsiElementPointer<PsiElement>?
  val file: VirtualFile?
}

/** Creates the default target, see [ProjectViewPsiSelectTarget]. */
fun createProjectViewPsiSelectTarget(
  elementPointer: SmartPsiElementPointer<PsiElement>?,
  file: VirtualFile?,
): ProjectViewPsiSelectTarget = ProjectViewPsiSelectTargetImpl(elementPointer, file)

private class ProjectViewPsiSelectTargetImpl(
  override val elementPointer: SmartPsiElementPointer<PsiElement>?,
  override val file: VirtualFile?,
) : ProjectViewPsiSelectTarget {
  override fun toString(): String = "ProjectViewPsiSelectTarget(elementPointer=$elementPointer, file=$file)"
}
