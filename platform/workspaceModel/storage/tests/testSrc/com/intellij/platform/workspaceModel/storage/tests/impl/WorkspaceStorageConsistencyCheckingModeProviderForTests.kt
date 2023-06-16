// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspaceModel.storage.tests.impl

import com.intellij.workspaceModel.storage.impl.ConsistencyCheckingMode
import com.intellij.workspaceModel.storage.impl.ConsistencyCheckingModeProvider

class WorkspaceStorageConsistencyCheckingModeProviderForTests : ConsistencyCheckingModeProvider {
  override val mode: ConsistencyCheckingMode
    get() = ConsistencyCheckingMode.SYNCHRONOUS
}