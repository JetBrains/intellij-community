/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui;

import com.intellij.cvsSupport2.config.DateOrRevisionSettings;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.text.SyncDateFormat;
import com.michaelbaranov.microba.calendar.DatePicker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * author: lesya
 */
public class DateOrRevisionOrTagSettings {

  @NonNls private static final String FORMAT = "EEE MMM dd HH:mm:ss yyyy";
  private static final SyncDateFormat CVS_FORMAT = new SyncDateFormat(new SimpleDateFormat(FORMAT, Locale.US));

  private JRadioButton myUseBranch;
  private JRadioButton myUseDate;
  private JRadioButton myUseHead;
  private TextFieldWithBrowseButton myBranch;
  private JPanel myPanel;
  private DatePicker myDatePicker;
  private final Project myProject;

  private final TagsProvider myTagsProvider;
  private static final String EMPTY_DATE = "";

  public DateOrRevisionOrTagSettings(TagsProvider tagsProvider, Project project, final boolean forTemporaryConfiguration) {
    myTagsProvider = tagsProvider;
    myProject = project;
    myDatePicker.setDateFormat(DateFormatUtil.getDateTimeFormat().getDelegate());

    myUseBranch.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        refreshEnabling();
        if (myUseBranch.isEnabled()){
          myBranch.getTextField().selectAll();
          myBranch.getTextField().requestFocus();
        }
      }
    });

    myUseHead.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        refreshEnabling();
      }
    });

    myUseDate.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myUseDate.isSelected() && getDateString().equals(EMPTY_DATE)) {
          setDate(new Date());
        }
        refreshEnabling();
        if (myUseDate.isEnabled()){
          myDatePicker.requestFocus();
        }

      }
    });

    myBranch.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String tagName = TagsHelper.chooseBranch(myTagsProvider, myProject, forTemporaryConfiguration);
        if (tagName != null) myBranch.setText(tagName);
      }
    });
    refreshEnabling();
  }

  private void setDate(Date date) {
    try {
      myDatePicker.setDate(date);
    }
    catch (PropertyVetoException e) {
      // ignore
    }
  }

  public void updateFrom(@NotNull DateOrRevisionSettings settings) {
    myUseHead.setSelected((!settings.USE_BRANCH) && (!settings.USE_DATE));
    myUseBranch.setSelected(settings.USE_BRANCH);
    myUseDate.setSelected(settings.USE_DATE);
    myBranch.setText(settings.BRANCH);
    updateDate(settings.getDate());
    refreshEnabling();
  }

  public void saveTo(DateOrRevisionSettings settings) {
    settings.USE_BRANCH = myUseBranch.isSelected();
    settings.USE_DATE = myUseDate.isSelected();
    settings.BRANCH = getBranchText();
    settings.setDate(getDateString());
  }

  private String getBranchText() {
    return myBranch.getText().trim();
  }

  private String getDateString() {
    return CVS_FORMAT.format(myDatePicker.getDate());
  }

  private void refreshEnabling() {

    boolean useBranch = myUseBranch.isSelected();
    boolean useDate = myUseDate.isSelected();

    myBranch.setEnabled(useBranch);
    myBranch.setEditable(useBranch);

    myDatePicker.setEnabled(useDate);

    myBranch.getButton().setEnabled(useBranch);
  }

  private void updateDate(String dateString) {
    final Date date;
    try {
      date = CVS_FORMAT.parse(dateString);
    }
    catch (ParseException e) {
      // ignore
      return;
    }
    try {
      myDatePicker.setDate(date);
    }
    catch (PropertyVetoException e) {
      // ignore
    }
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void setHeadCaption(String text){
    myUseHead.setText(text);
  }

}
