// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

@Deprecated
// to be deleted in 2018.3
// it's an example of using contexts for usages(metrics)
//   "id": "statistics.ui.font.family",
//          "metrics": [
//            {
//              "id": "ui.font",
//              "value": 1,
//              "context": {
//                "data_1": "Mac OS X",
//                "data_2": "12",
//                "data_3": "Lucida Grande"
//              }
//            }
//
public class FontUsagesCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    UISettingsState settings = UISettings.getShadowInstance().getState();

    FUSUsageContext context = FUSUsageContext.create(FUSUsageContext.getOSNameContextData(),
                                                     Integer.toString(settings.getFontSize()), settings.getFontFace());

    return Collections.singleton(new UsageDescriptor("ui.font", 1, context));
  }


  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.ui.font.family";
  }
}