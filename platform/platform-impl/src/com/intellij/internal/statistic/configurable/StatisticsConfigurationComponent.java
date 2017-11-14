// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.configurable;

import com.intellij.internal.statistic.StatisticsBundle;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.ui.RelativeFont;

import javax.swing.*;
import java.util.Map;

public class StatisticsConfigurationComponent {

  private JPanel myMainPanel;
  private JLabel myTitle;
  private JCheckBox myAllowToSendUsagesCheckBox;
  private JLabel myLabel;

  public StatisticsConfigurationComponent() {
    String product = ApplicationNamesInfo.getInstance().getFullProductName();
    String company = ApplicationInfo.getInstance().getCompanyName();
    myTitle.setText(StatisticsBundle.message("stats.title", product, company));
    myLabel.setText(StatisticsBundle.message("stats.config.details", company));
    RelativeFont.SMALL.install(myLabel);

    myAllowToSendUsagesCheckBox.setText(StatisticsBundle.message("stats.config.allow.send.stats.text", company));

    // Let current statistics service override labels
    StatisticsService service = StatisticsUploadAssistant.getStatisticsService();
    if (service != null) {
      Map<String, String> overrides = service.getStatisticsConfigurationLabels();
      if (overrides != null) {
        String s = overrides.get(StatisticsService.TITLE);
        if (s != null) {
          myTitle.setText(s);
        }
        s = overrides.get(StatisticsService.DETAILS);
        if (s != null) {
          myLabel.setText(s);
        }
        s = overrides.get(StatisticsService.ALLOW_CHECKBOX);
        if (s != null) {
          myAllowToSendUsagesCheckBox.setText(s);
        }
      }
    }

    myTitle.setText(myTitle.getText().replace("%company%", company));
    myLabel.setText(myLabel.getText().replace("%company%", company));
    myAllowToSendUsagesCheckBox.setText(myAllowToSendUsagesCheckBox.getText().replace("%company%", company));
  }

  public JPanel getJComponent() {
    return myMainPanel;
  }

  public boolean isAllowed() {
    return myAllowToSendUsagesCheckBox.isSelected();
  }

  public void reset() {
    myAllowToSendUsagesCheckBox.setSelected(UsageStatisticsPersistenceComponent.getInstance().isAllowed());
  }

  public SendPeriod getPeriod() {
    return SendPeriod.DAILY;
  }
}
