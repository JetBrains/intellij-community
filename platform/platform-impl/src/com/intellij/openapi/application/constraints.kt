// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Experimental

package com.intellij.openapi.application

import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.application.impl.InSmartMode
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.application.impl.WithDocumentsCommitted as DocumentsCommitted

interface ReadConstraints {

  fun withConstraint(constraint: ContextConstraint): ReadConstraints

  fun inSmartMode(project: Project): ReadConstraints

  fun withDocumentsCommitted(project: Project): ReadConstraints

  /**
   * This function is called in read action.
   *
   * @return unsatisfied constraint if any, or `null` if all constraints are satisfied
   */
  fun findUnsatisfiedConstraint(): ContextConstraint?

  companion object {

    private val unconstrained: ReadConstraints = DefaultReadConstraints(emptyList())

    fun unconstrained(): ReadConstraints = unconstrained

    fun inSmartMode(project: Project): ReadConstraints = unconstrained.inSmartMode(project)

    fun withDocumentsCommitted(project: Project): ReadConstraints = unconstrained.withDocumentsCommitted(project)
  }
}

private class DefaultReadConstraints(
  private val constraints: Collection<ContextConstraint>
) : ReadConstraints {

  override fun withConstraint(constraint: ContextConstraint): ReadConstraints = DefaultReadConstraints(constraints + constraint)

  override fun inSmartMode(project: Project): ReadConstraints = withConstraint(InSmartMode(project))

  override fun withDocumentsCommitted(project: Project): ReadConstraints = withConstraint(DocumentsCommitted(project, ModalityState.any()))

  override fun findUnsatisfiedConstraint(): ContextConstraint? {
    return constraints.firstOrNull {
      !it.isCorrectContext()
    }
  }
}
