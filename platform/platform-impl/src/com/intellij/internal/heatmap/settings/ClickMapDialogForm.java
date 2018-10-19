// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.heatmap.settings;

import com.intellij.internal.heatmap.actions.ProductBuildInfo;
import com.intellij.internal.heatmap.actions.ShareType;
import com.intellij.internal.heatmap.actions.ShowHeatMapAction;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ComboBoxWithHistory;
import com.michaelbaranov.microba.calendar.DatePicker;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.beans.PropertyVetoException;
import java.util.List;
import java.util.*;

public class ClickMapDialogForm {

  private static final Comparator<String> VERSIONS_COMPARATOR = (o1, o2) -> {
    try {
      Integer major1 = Integer.parseInt(o1.substring(0, o1.indexOf('.')));
      Integer major2 = Integer.parseInt(o2.substring(0, o2.indexOf('.')));
      int result = major2.compareTo(major1);
      if (result != 0) return result;

      Integer minor1 = Integer.parseInt(o1.substring(o1.indexOf('.'), o1.lastIndexOf('.')));
      Integer minor2 = Integer.parseInt(o2.substring(o2.indexOf('.'), o2.lastIndexOf('.')));
      result = minor2.compareTo(minor1);
      if (result != 0) return result;

      Integer bugFix1 = Integer.parseInt(o1.substring(o1.lastIndexOf('.')));
      Integer bugFix2 = Integer.parseInt(o2.substring(o2.lastIndexOf('.')));
      return bugFix2.compareTo(bugFix1);
    }
    catch (NumberFormatException e) {
      return o2.compareTo(o1);
    }
  };
  private ComboBoxWithHistory myServiceUrlsCombo;
  private JRadioButton myByToolbarShare;
  private JRadioButton myByGroupShare;
  private DatePicker myStartDatePicker;
  private DatePicker myEndDatePicker;
  private JPasswordField myAccessTokenField;
  private JPanel myJPanel;
  private JCheckBox myRememberTokenCheckBox;
  private JList<String> myProductVersionsJList;
  private JCheckBox myIncludeEAPCheckBox;
  private JCheckBox myFilterVersionsCheckBox;
  private ColorPanel myColorChooserPanel;

  public final String getServiceUrl() {
    return myServiceUrlsCombo.getModel().getSelectedItem().toString();
  }

  public boolean filterVersions() {
    return myFilterVersionsCheckBox.isSelected();
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myFilterVersionsCheckBox;
  }

  @NotNull
  public final ShareType getShareType() {
    return myByToolbarShare.isSelected() ? ShareType.BY_PLACE : ShareType.BY_GROUP;
  }

  public void setShareType(ShareType shareType) {
    if (shareType == ShareType.BY_GROUP) {
      myByGroupShare.setSelected(true);
    }
    else {
      myByToolbarShare.setSelected(true);
    }
  }

  public Color getSelectedColor() {
    return myColorChooserPanel.getSelectedColor();
  }

  public void setSelectedColor(Color color) {
    myColorChooserPanel.setSelectedColor(color);
  }

  public DatePicker getMyStartDatePicker() {
    return myStartDatePicker;
  }

  public DatePicker getMyEndDatePicker() {
    return myEndDatePicker;
  }

  public ClickMapDialogForm() {
    final ButtonGroup myShareButtonsGroup = new ButtonGroup();
    myShareButtonsGroup.add(myByToolbarShare);
    myShareButtonsGroup.add(myByGroupShare);
    myServiceUrlsCombo.setEditable(true);
    myJPanel.setVisible(true);
    DefaultListModel<String> jListModel = (DefaultListModel<String>)myProductVersionsJList.getModel();
    SortedSet<String> ideVersions = new TreeSet<>(VERSIONS_COMPARATOR);
    for (final ProductBuildInfo buildInfo : ShowHeatMapAction.MetricsCache.getOurIdeBuildInfos()) {
      ideVersions.add(buildInfo.getVersion());
    }

    for (final String ideVersion : ideVersions) {
      jListModel.addElement(ideVersion);
    }
    myFilterVersionsCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateSelectVersionsState();
      }
    });
    updateSelectVersionsState();
  }

  private void updateSelectVersionsState() {
    myProductVersionsJList.setEnabled(myFilterVersionsCheckBox.isSelected());
    myIncludeEAPCheckBox.setEnabled(myFilterVersionsCheckBox.isSelected());
  }

  public List<String> getSelectedVersions() {
    return myProductVersionsJList.getSelectedValuesList();
  }

  public void setStartDate(Date date) {
    try {
      myStartDatePicker.setDate(date);
    }
    catch (PropertyVetoException ignore) {
    }
  }

  public void setEndDate(Date date) {
    try {
      myEndDatePicker.setDate(date);
    }
    catch (PropertyVetoException ignore) {
    }
  }

  public void setServiceUrls(List<String> urls) {
    for (final String url : urls) {
      //noinspection unchecked
      myServiceUrlsCombo.addItem(url);
    }
  }

  public void setSelectedItem(String url) {
    myServiceUrlsCombo.setSelectedItem(url);
  }

  public JPanel getMyJPanel() {
    return myJPanel;
  }

  public JPasswordField getMyAccessTokenField() {
    return myAccessTokenField;
  }

  private void createUIComponents() {
    myServiceUrlsCombo = new ComboBoxWithHistory("com.intellij.plugin.heatmap.settings.history");
    myProductVersionsJList = new JBList<>(new DefaultListModel<>());
    myColorChooserPanel = new ColorPanel();
    myColorChooserPanel.setSelectedColor(JBColor.RED);
  }

  public void setRememberTokenSelected(boolean value) {
    myRememberTokenCheckBox.setSelected(value);
  }

  public void setMyFilterVersionsSelected(boolean value) {
    myFilterVersionsCheckBox.setSelected(value);
  }

  public boolean getMyRememberTokenSelected() {
    return myRememberTokenCheckBox.isSelected();
  }

  public boolean getMyFilterVersionsSelected() {
    return myFilterVersionsCheckBox.isSelected();
  }

  public boolean getMyIncludeEAPSelected() {
    return myIncludeEAPCheckBox.isSelected();
  }

  public void setMyIncludeEAPSelected(boolean value) {
    myIncludeEAPCheckBox.setSelected(value);
  }

  public void setSelectedVersions(List<String> ideVersions) {
    myProductVersionsJList.setSelectedIndices(getIndicesForValues(myProductVersionsJList, ideVersions));
  }

  private static int[] getIndicesForValues(@NotNull JList<String> jList, List<String> values) {
    ListModel<String> model = jList.getModel();
    List<Integer> resultList = ContainerUtil.newArrayList();
    for (int i = 0; i < model.getSize(); i++) {
      String element = model.getElementAt(i);
      if (values.contains(element)) resultList.add(i);
    }
    int[] resultArray = new int[resultList.size()];
    for (int i = 0; i < resultList.size(); i++) resultArray[i] = resultList.get(i);
    return resultArray;
  }
}
