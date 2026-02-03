// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.PluginBundledTemplate;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector.FILE_TEMPLATE_NAME;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoByDescriptor;

final class BundledFileTemplateValidationRule extends CustomValidationRule {
  @Override
  public @NotNull String getRuleId() {
    return FILE_TEMPLATE_NAME;
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    for (FileTemplate template : FileTemplateManager.getDefaultInstance().getInternalTemplates()) {
      if (template instanceof PluginBundledTemplate) {
        if (StringUtil.equals(template.getName(), data)) {
          PluginDescriptor plugin = ((PluginBundledTemplate)template).getPluginDescriptor();
          PluginInfo pluginInfo = getPluginInfoByDescriptor(plugin);
          if (pluginInfo.isSafeToReport()) {
            return ValidationResultType.ACCEPTED;
          }
          break;
        }
      }
    }

    return ValidationResultType.THIRD_PARTY;
  }
}