// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.platform.workspace.storage.metadata.MetadataStorageBridge
import org.jetbrains.kotlin.idea.core.script.MetadataStorageImpl

object MetadataStorageImpl: MetadataStorageBridge(MetadataStorageImpl)
