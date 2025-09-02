// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalFileCustomValidationRule

internal class FileExtensionValidationRule : LocalFileCustomValidationRule(
  "file.extension.validation.rule",
  FileExtensionValidationRule::class.java,
  "allowed_file_extensions.txt"
)