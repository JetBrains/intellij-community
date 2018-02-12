/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollingUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FileTypeChooserDialogImpl extends DialogWrapper implements FileTypeChooserDialog {
  private JList myList;
  private JLabel myTitleLabel;
  private ComboBox myPattern;
  private JPanel myPanel;
  private JRadioButton myOpenInIdea;
  private JRadioButton myOpenAsNative;
  private final String myFileName;

  FileTypeChooserDialogImpl(@NotNull List<String> patterns, @NotNull String fileName) {
    super(true);
    myFileName = fileName;

    myOpenInIdea.setText("Open matching files in " + ApplicationNamesInfo.getInstance().getFullProductName() + ":");

    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    Arrays.sort(fileTypes, new Comparator<FileType>() {
      @Override
      public int compare(final FileType fileType1, final FileType fileType2) {
        if (fileType1 == null) {
          return 1;
        }
        if (fileType2 == null) {
          return -1;
        }
        return fileType1.getDescription().compareToIgnoreCase(fileType2.getDescription());
      }
    });

    final DefaultListModel model = new DefaultListModel();
    for (FileType type : fileTypes) {
      if (!type.isReadOnly() && type != FileTypes.UNKNOWN && !(type instanceof NativeFileType)) {
        model.addElement(type);
      }
    }
    myList.setModel(model);
    new ListSpeedSearch(myList, (Function<Object, String>)o -> ((FileType)o).getName());
    myPattern.setModel(new CollectionComboBoxModel(ContainerUtil.map(patterns, FunctionUtil.<String>id()), patterns.get(0)));

    setTitle(FileTypesBundle.message("filetype.chooser.title"));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    myTitleLabel.setText(FileTypesBundle.message("filetype.chooser.prompt", myFileName));

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new FileTypeRenderer());

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myList);

    myList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          updateButtonsState();
        }
      }
    );

    ScrollingUtil.selectItem(myList, FileTypes.PLAIN_TEXT);

    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  private void updateButtonsState() {
    setOKActionEnabled(myList.getSelectedIndex() != -1 || myOpenAsNative.isSelected());
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.fileTypes.FileTypeChooser";
  }

  @Override
  public FileType getSelectedType() {
    return myOpenAsNative.isSelected() ? NativeFileType.INSTANCE : (FileType) myList.getSelectedValue();
  }

  @Override
  @NotNull
  public String getSelectedItem() {
    return (String)myPattern.getSelectedItem();
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.register.association";
  }
}
