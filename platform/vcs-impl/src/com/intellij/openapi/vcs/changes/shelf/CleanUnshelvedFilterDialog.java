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

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Calendar;

public class CleanUnshelvedFilterDialog extends DialogWrapper {
  private final JRadioButton myUnshelvedWithFilterButton;
  private final JRadioButton myAllUnshelvedButton;
  private final ComboBox<TimePeriod> myTimePeriodComboBox;

  private enum TimePeriod {
    Week {
      @Override
      protected void updateCalendar(@NotNull Calendar cal) {
        cal.add(Calendar.DAY_OF_MONTH, -7);
      }
    }, Month {
      @Override
      protected void updateCalendar(@NotNull Calendar cal) {
        cal.add(Calendar.MONTH, -1);
      }
    }, Year {
      @Override
      protected void updateCalendar(@NotNull Calendar cal) {
        cal.add(Calendar.YEAR, -1);
      }
    };

    public long getTimeLimitInMillis() {
      Calendar cal = Calendar.getInstance();
      updateCalendar(cal);
      return cal.getTimeInMillis();
    }

    protected abstract void updateCalendar(@NotNull Calendar cal);
  }

  public CleanUnshelvedFilterDialog(@Nullable Project project) {
    super(project);
    setTitle(VcsBundle.message("shelve.clean.unshelved.changelists.title"));
    myUnshelvedWithFilterButton = new JRadioButton(VcsBundle.message("shelve.clean.changelists.older.than.one.button"), true);
    myAllUnshelvedButton = new JRadioButton(VcsBundle.message("shelve.clean.changelists.all.button"), false);
    myTimePeriodComboBox = new ComboBox<>(new EnumComboBoxModel<>(TimePeriod.class));
    myTimePeriodComboBox.setRenderer(SimpleListCellRenderer.create("", value -> {
      switch (value) {
        case Week:
          return VcsBundle.message("shelve.clean.timeperiod.week");
        case Month:
          return VcsBundle.message("shelve.clean.timeperiod.month");
        case Year:
          return VcsBundle.message("shelve.clean.timeperiod.year");
        default:
          throw new IllegalArgumentException(value.name());
      }
    }));
    myTimePeriodComboBox.setEnabled(myUnshelvedWithFilterButton.isSelected());
    myUnshelvedWithFilterButton.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myTimePeriodComboBox.setEnabled(myUnshelvedWithFilterButton.isSelected());
      }
    });
    setOKButtonText(CommonBundle.message("button.delete"));
    init();
    setResizable(false);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JLabel questLabel = new JLabel(VcsBundle.message("shelve.delete.already.unshelved.label"));
    String panelConstraints = "flowx, ins 0"; //NON-NLS
    final MigLayout migLayout = new MigLayout(panelConstraints);
    JPanel buttonsPanel = new JPanel(migLayout);
    ButtonGroup gr = new ButtonGroup();
    gr.add(myUnshelvedWithFilterButton);
    gr.add(myAllUnshelvedButton);

    myUnshelvedWithFilterButton.setBorder(BorderFactory.createEmptyBorder());
    myAllUnshelvedButton.setBorder(BorderFactory.createEmptyBorder());

    JPanel filterPanel = new JPanel(new MigLayout(panelConstraints));
    filterPanel.add(myUnshelvedWithFilterButton);
    filterPanel.add(myTimePeriodComboBox);
    buttonsPanel.add(filterPanel, "wrap"); //NON-NLS
    buttonsPanel.add(myAllUnshelvedButton);
    panel.add(questLabel, BorderLayout.NORTH);
    panel.add(buttonsPanel, BorderLayout.CENTER);

    return panel;
  }

  public boolean isAllUnshelvedSelected() {
    return myAllUnshelvedButton.isSelected();
  }

  public boolean isUnshelvedWithFilterSelected() {
    return myUnshelvedWithFilterButton.isSelected();
  }

  public long getTimeLimitInMillis() {
    return ((TimePeriod)myTimePeriodComboBox.getSelectedItem()).getTimeLimitInMillis();
  }
}
