// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.ReadActionSupport
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable

internal class PlatformReadActionSupport : ReadActionSupport {

  override fun smartModeConstraint(project: Project): ReadConstraint {
    check(!LightEdit.owns(project)) {
      "ReadConstraint.inSmartMode() can't be used in LightEdit mode, check that LightEdit.owns(project)==false before calling"
    }
    return SmartModeReadConstraint(project)
  }

  override fun committedDocumentsConstraint(project: Project): ReadConstraint {
    return CommittedDocumentsConstraint(project)
  }

  override suspend fun <X> executeReadAction(
    constraints: List<ReadConstraint>,
    undispatched: Boolean,
    blocking: Boolean,
    action: () -> X,
  ): X {
    return InternalReadAction(constraints, undispatched, blocking, action).runReadAction()
  }

  override fun <X, E : Throwable> computeCancellable(action: ThrowableComputable<X, E>): X {
    return cancellableReadAction {
      action.compute()
    }
  }
}
