// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("VirtualFileUrlManagerUtil")

package com.intellij.workspaceModel.ide

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

fun VirtualFileUrl.isEqualOrParentOf(other: VirtualFileUrl): Boolean = FileUtil.startsWith(other.url.removeSuffix("/"), url.removeSuffix("/"))

fun VirtualFileUrl.toPath(): Path = Path.of(JpsPathUtil.urlToPath(url))