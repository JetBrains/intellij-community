// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.versionDownloadManager

import com.intellij.util.text.SemVer
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface ExecutableResourceVdm {
  fun getExecutablePath(version: SemVer): Path?
}
