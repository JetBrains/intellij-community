/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.IconUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.Calendar;

public class CleanUnshelvedFilterDialog extends DialogWrapper {
  private final JRadioButton mySystemUnshelvedButton;
  private final JRadioButton myUnshelvedWithFilterButton;
  private final ComboBox myTimePeriodComboBox;
  private static Icon DISABLED_BIN_ICON = IconUtil.desaturate(AllIcons.Actions.GC);

  enum TimePeriod {
    Week, Month, Year
  }

  public CleanUnshelvedFilterDialog(@Nullable Project project) {
    super(project);
    setTitle("Clean Unshelved Changelists");
    mySystemUnshelvedButton = new JRadioButton("System unshelved changelists marked to be deleted", true);
    myUnshelvedWithFilterButton = new JRadioButton("All unshelved changelists older than one", false);
    myTimePeriodComboBox = new ComboBox(new CollectionComboBoxModel<TimePeriod>(Arrays.asList(TimePeriod.values())));
    myTimePeriodComboBox.setEnabled(myUnshelvedWithFilterButton.isSelected());
    myUnshelvedWithFilterButton.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myTimePeriodComboBox.setEnabled(myUnshelvedWithFilterButton.isSelected());
      }
    });
    init();
    setResizable(false);
  }

  @Override
  public boolean isOKActionEnabled() {
    return isSystemUnshelvedMarked() || isUnshelvedWithFilterMarked();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JLabel questLabel = new JLabel("Would you like to delete: \n");
    final MigLayout migLayout = new MigLayout("flowx, ins 0");
    JPanel buttonsPanel = new JPanel(migLayout);
    ButtonGroup gr = new ButtonGroup();
    gr.add(mySystemUnshelvedButton);
    gr.add(myUnshelvedWithFilterButton);

    mySystemUnshelvedButton.setBorder(BorderFactory.createEmptyBorder());
    myUnshelvedWithFilterButton.setBorder(BorderFactory.createEmptyBorder());


    buttonsPanel.add(mySystemUnshelvedButton);
    final JLabel iconLabel = new JLabel(DISABLED_BIN_ICON);
    iconLabel.setBorder(BorderFactory.createEmptyBorder());
    buttonsPanel.add(iconLabel, "wrap");

    JPanel filterPanel = new JPanel(migLayout);
    filterPanel.add(myUnshelvedWithFilterButton);
    filterPanel.add(myTimePeriodComboBox);
    buttonsPanel.add(filterPanel, "span 1 2");
    panel.add(questLabel, BorderLayout.NORTH);
    panel.add(buttonsPanel, BorderLayout.CENTER);

    return panel;
  }

  public boolean isSystemUnshelvedMarked() {
    return mySystemUnshelvedButton.isSelected();
  }

  public boolean isUnshelvedWithFilterMarked() {
    return myUnshelvedWithFilterButton.isSelected();
  }

  public long getTimeLimitInMillis() {
    TimePeriod tp = (TimePeriod)myTimePeriodComboBox.getSelectedItem();
    Calendar cal = Calendar.getInstance();
    if (tp == TimePeriod.Week) {
      cal.add(Calendar.DAY_OF_MONTH, -7);
    }
    else if (tp == TimePeriod.Month) {
      cal.add(Calendar.MONTH, -1);
    }
    else {
      cal.add(Calendar.YEAR, -1);
    }
    return cal.getTimeInMillis();
  }
}
