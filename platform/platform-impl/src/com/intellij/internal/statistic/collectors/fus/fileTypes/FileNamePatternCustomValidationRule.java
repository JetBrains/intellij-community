// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector.FILE_NAME_PATTERN;

final class FileNamePatternCustomValidationRule extends CustomValidationRule {
  @Override
  public @NotNull String getRuleId() {
    return FILE_NAME_PATTERN;
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull IEventContext context) {
    var fileTypeName = context.getEventData().get("file_type");
    var fileType = fileTypeName != null ? FileTypeManager.getInstance().findFileTypeByName(fileTypeName.toString()) : null;
    if (fileType == null || fileType == UnknownFileType.INSTANCE) {
      return ValidationResultType.THIRD_PARTY;
    }

    var fileTypeManager = FileTypeManager.getInstance();
    if (!(fileTypeManager instanceof FileTypeManagerImpl ftmImpl)) {
      return ValidationResultType.THIRD_PARTY;
    }

    var fileNameMatchers = ftmImpl.getStandardMatchers(fileType);
    var fileNameMatcher = fileNameMatchers.stream().filter(x -> x.getPresentableString().equals(data)).findFirst();
    if (fileNameMatcher.isEmpty()) {
      return ValidationResultType.THIRD_PARTY;
    }

    return acceptWhenReportedByJetBrainsPlugin(context);
  }
}
