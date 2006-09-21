package com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui;

import com.intellij.cvsSupport2.config.DateOrRevisionSettings;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.text.SyncDateFormat;
import com.intellij.util.ui.SelectDateDialog;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
  private static final SyncDateFormat PRESENTABLE_FORMAT = new SyncDateFormat(SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT,
                                                                                                                   SimpleDateFormat.SHORT,
                                                                                                                   Locale.getDefault()));

  private JRadioButton myUseBranch;
  private JRadioButton myUseDate;
  private JRadioButton myUseHead;
  private TextFieldWithBrowseButton myBranch;
  private TextFieldWithBrowseButton myDate;
  private JPanel myPanel;
  private final Project myProject;

  private TagsProvider myTagsProvider;
  private static final String EMPTY_DATE = "";

  public DateOrRevisionOrTagSettings(TagsProvider tagsProvider, Project project, final boolean forTemporaryConfiguration) {
    myTagsProvider = tagsProvider;
    myProject = project;

    ButtonGroup mergingGroup = new ButtonGroup();
    mergingGroup.add(myUseBranch);
    mergingGroup.add( myUseDate);
    mergingGroup.add( myUseHead);

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
          myDate.getButton().requestFocus();
        }

      }
    });

    myBranch.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String tagName = TagsHelper.chooseBranch(myTagsProvider, myProject, forTemporaryConfiguration);
        if (tagName != null) myBranch.setText(tagName);
      }
    });

    myDate.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        chooseDate();
      }
    });

    refreshEnabling();
  }

  private boolean chooseDate() {
    SelectDateDialog selectDateDialog = new SelectDateDialog(myProject);
    selectDateDialog.setDate(getDate());
    selectDateDialog.show();
    if (selectDateDialog.isOK()) {
      setDate(selectDateDialog.getDate());
      return true;
    }
    return false;
  }

  private void setDate(Date date) {
    myDate.setText(PRESENTABLE_FORMAT.format(date));
  }

  public void updateFrom(DateOrRevisionSettings settings) {
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
    String dateString = getDateStringValue();
    if (dateString.length() > 0) {
      return CVS_FORMAT.format(getDate());
    }
    else {
      return "";
    }
  }

  private String getDateStringValue() {
    return myDate.getText();
  }

  private void refreshEnabling() {

    boolean useBranch = myUseBranch.isSelected();
    boolean useDate = myUseDate.isSelected();

    myBranch.setEnabled(useBranch);
    myBranch.setEditable(useBranch);

    myDate.getTextField().setEditable(false);
    myDate.setEnabled(useDate);

    myDate.getButton().setEnabled(useDate);
    myBranch.getButton().setEnabled(useBranch);
  }

  private void updateDate(String dateString) {
    try {
      myDate.setText(PRESENTABLE_FORMAT.format(CVS_FORMAT.parse(dateString)));
    }
    catch (ParseException e) {
      myDate.setText("");
    }
  }

  private Date getDate() {
    try {
      return PRESENTABLE_FORMAT.parse(getDateStringValue());
    }
    catch (ParseException e) {
      return new Date();
    }
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void setHeadCaption(String text){
    myUseHead.setText(text);
  }

}
