// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification

internal class XDebuggerEvaluationEditorPopupGroup : DefaultActionGroup(), ActionRemoteBehaviorSpecification.Frontend
