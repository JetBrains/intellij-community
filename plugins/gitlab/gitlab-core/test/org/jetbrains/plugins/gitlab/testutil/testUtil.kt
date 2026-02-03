// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.testutil

import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import kotlin.io.path.exists

fun getGitLabTestDataPath(at: String): Path? =
  Path.of(PathManager.getHomePath(), at).takeIf { it.exists() } ?:
  Path.of(PathManager.getHomePath()).parent.resolve(at).takeIf { it.exists() }