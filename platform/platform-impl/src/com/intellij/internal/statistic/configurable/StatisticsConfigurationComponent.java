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

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.ex.MultiLineLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class StatisticsConfigurationComponent {

  private JPanel myMainPanel;
  private JLabel myTitle;
  private JCheckBox myAllowToSendUsagesCheckBox;
  private JRadioButton myDailyRadioButton;
  private JRadioButton myMonthlyRadioButton;
  private JRadioButton myWeeklyRadioButton;
  private JLabel myLabel;
  private JPanel myRadioButtonPanel;

  public StatisticsConfigurationComponent() {
    myTitle.setText("Help improve "+ ApplicationNamesInfo.getInstance().getFullProductName() + " by sending anonymous usage statistics to JetBrains");
    myLabel.setText("<html>We're asking your permission to send information about your plugins configuration (what is enabled and what is not) <br> and feature usage statistics (e.g. how frequently you're using code completion).<br>    This data is anonymous, does not contain any personal information, collected for use only by JetBrains<br> and will never be transmitted to any third party.</html>");

    myAllowToSendUsagesCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setRadioButtonsEnabled();
      }
    });
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
