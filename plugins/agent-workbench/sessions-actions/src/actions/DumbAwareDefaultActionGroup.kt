// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsActions

@Suppress("ActionPresentationInstantiatedInCtor")
internal class DumbAwareDefaultActionGroup : DefaultActionGroup, DumbAware {
  constructor() : super()

  constructor(@NlsActions.ActionText text: String?, popup: Boolean) : super(text, popup)
}
