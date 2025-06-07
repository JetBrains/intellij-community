// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

import static com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector.FILE_NAME_PATTERN;

final class FileNamePatternCustomValidationRule extends CustomValidationRule {
  @Override
  public @NotNull String getRuleId() {
    return FILE_NAME_PATTERN;
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    final Object fileTypeName = context.eventData.get("file_type");
    final FileType fileType = fileTypeName != null ? FileTypeManager.getInstance().findFileTypeByName(fileTypeName.toString()) : null;
    if (fileType == null || fileType == UnknownFileType.INSTANCE) {
      return ValidationResultType.THIRD_PARTY;
    }

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (!(fileTypeManager instanceof FileTypeManagerImpl)) {
      return ValidationResultType.THIRD_PARTY;
    }
    List<FileNameMatcher> fileNameMatchers = ((FileTypeManagerImpl)fileTypeManager).getStandardMatchers(fileType);
    Optional<FileNameMatcher> fileNameMatcher = fileNameMatchers.stream().filter(x -> x.getPresentableString().equals(data)).findFirst();
    if (fileNameMatcher.isEmpty()) {
      return ValidationResultType.THIRD_PARTY;
    }

    return acceptWhenReportedByJetBrainsPlugin(context);
  }
}