// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil

import com.intellij.openapi.vcs.FilePath
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object VcsFileUtilKt {
  @JvmStatic
  fun isUnder(repositoryRootPath: FilePath, parents: Set<FilePath>, child: FilePath) =
    generateSequence(child) { if (repositoryRootPath == it) null else it.parentPath }.any { it in parents }
}