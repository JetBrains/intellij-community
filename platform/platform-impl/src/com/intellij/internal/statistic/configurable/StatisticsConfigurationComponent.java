/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.configurable;

import com.intellij.internal.statistic.StatisticsBundle;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.ui.RelativeFont;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

public class StatisticsConfigurationComponent {

  private JPanel myMainPanel;
  private JLabel myTitle;
  private JCheckBox myAllowToSendUsagesCheckBox;
  private JRadioButton myDailyRadioButton;
  private JRadioButton myMonthlyRadioButton;
  private JRadioButton myWeeklyRadioButton;
  private JLabel myLabel;

  public StatisticsConfigurationComponent() {
    String product = ApplicationNamesInfo.getInstance().getFullProductName();
    String company = ApplicationInfo.getInstance().getCompanyName();
    myTitle.setText(StatisticsBundle.message("stats.title", product, company));
    myLabel.setText(StatisticsBundle.message("stats.config.details", company));
    RelativeFont.SMALL.install(myLabel);

    myAllowToSendUsagesCheckBox.setText(StatisticsBundle.message("stats.config.allow.send.stats.text", company));
    myAllowToSendUsagesCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setRadioButtonsEnabled();
      }
    });

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

  private void setRadioButtonsEnabled() {
    final boolean enabled = myAllowToSendUsagesCheckBox.isSelected();

    myWeeklyRadioButton.setEnabled(enabled);
    myMonthlyRadioButton.setEnabled(enabled);
    myDailyRadioButton.setEnabled(enabled);
  }

  public JPanel getJComponent() {
    return myMainPanel;
  }

  public boolean isAllowed() {
    return myAllowToSendUsagesCheckBox.isSelected();
  }

  public void reset() {
    final UsageStatisticsPersistenceComponent persistenceComponent = UsageStatisticsPersistenceComponent.getInstance();

    myAllowToSendUsagesCheckBox.setSelected(persistenceComponent.isAllowed());
    setRadioButtonsEnabled();

    final SendPeriod period = persistenceComponent.getPeriod();

    switch (period) {
      case DAILY:
        myDailyRadioButton.setSelected(true);
        break;
      case MONTHLY:
        myMonthlyRadioButton.setSelected(true);
        break;
      default:
        myWeeklyRadioButton.setSelected(true);
        break;
    }
  }

  public SendPeriod getPeriod() {
    if (myDailyRadioButton.isSelected()) return SendPeriod.DAILY;
    if (myMonthlyRadioButton.isSelected()) return SendPeriod.MONTHLY;

    return SendPeriod.WEEKLY;
  }
}
