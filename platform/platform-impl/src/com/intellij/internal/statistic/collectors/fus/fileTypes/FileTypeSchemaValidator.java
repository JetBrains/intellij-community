// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FileTypeSchemaValidator extends CustomValidationRule {
  static final ExtensionPointName<FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor>> EP =
    new ExtensionPointName<>("com.intellij.fileTypeUsageSchemaDescriptor");

  @Override
  public @NotNull String getRuleId() {
    return "file_type_schema";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

    for (FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor> ext : EP.getExtensionList()) {
      if (StringUtil.equals(ext.schema, data)) {
        return PluginInfoDetectorKt.getPluginInfo(ext.getInstance().getClass()).isSafeToReport() ?
               ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
      }
    }
    return ValidationResultType.REJECTED;
  }
}