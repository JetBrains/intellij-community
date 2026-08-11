// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.openapi.util.io.toCanonicalPath
import java.nio.file.Path

internal fun BuildIssue.quickFixIds(): List<String> =
  quickFixes.map { it.id.replace("\\(\\d+\\)$".toRegex(), "") }

internal fun BuildIssueQuickFix.isOpenFileQuickFix(path: Path): Boolean =
  this is OpenFileQuickFix && id == path.toCanonicalPath()

