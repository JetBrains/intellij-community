// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes.actions

import com.intellij.ide.actions.DeleteAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
// This class is needed only because we need this action to be declared in the frontend module, otherwise it will be called on backend.
class FrontendDeleteAction : DeleteAction()