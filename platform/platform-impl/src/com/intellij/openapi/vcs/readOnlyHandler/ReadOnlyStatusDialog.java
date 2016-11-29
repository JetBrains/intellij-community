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
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class ReadOnlyStatusDialog extends OptionsDialog {
  private static final SimpleTextAttributes BOLD_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.foreground());
  private static final SimpleTextAttributes SELECTED_BOLD_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, new JBColor(UIUtil::getListSelectionForeground));

  private JPanel myTopPanel;
  private JList myFileList;
  private JRadioButton myUsingFileSystemRadioButton;
  private JRadioButton myUsingVcsRadioButton;
  private JComboBox myChangelist;
  private FileInfo[] myFiles;

  public ReadOnlyStatusDialog(Project project, final FileInfo[] files) {
    super(project);
    setTitle(VcsBundle.message("dialog.title.clear.read.only.file.status"));
    myFiles = files;
    myFileList.setPreferredSize(getDialogPreferredSize());
    initFileList();
                       
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myChangelist.setEnabled(myUsingVcsRadioButton.isSelected());
      }
    };
    myUsingVcsRadioButton.addActionListener(listener);
    myUsingFileSystemRadioButton.addActionListener(listener);
    (myUsingVcsRadioButton.isEnabled() ? myUsingVcsRadioButton : myUsingFileSystemRadioButton).setSelected(true);
    myChangelist.setEnabled(myUsingVcsRadioButton.isSelected());

    //noinspection unchecked
    myFileList.setCellRenderer(new FileListRenderer());

    init();
  }

  @Override
  public long getTypeAheadTimeoutMs() {
    return Registry.intValue("actionSystem.typeAheadTimeBeforeDialog");
  }

  private void initFileList() {
    //noinspection unchecked
    myFileList.setModel(new AbstractListModel() {
      public int getSize() {
        return myFiles.length;
      }

      public Object getElementAt(final int index) {
        return myFiles[index].getFile();
      }
    });

    boolean hasVcs = false;
    for (FileInfo info : myFiles) {
      if (info.hasVersionControl()) {
        hasVcs = true;
        HandleType handleType = info.getSelectedHandleType();
        List<String> changelists = handleType.getChangelists();
        final String defaultChangelist = handleType.getDefaultChangelist();
        //noinspection unchecked
        myChangelist.setModel(new CollectionComboBoxModel(changelists, defaultChangelist));

        //noinspection unchecked
        myChangelist.setRenderer(new ColoredListCellRendererWrapper<String>() {
          @Override
          protected void doCustomize(JList list, String value, int index, boolean selected, boolean hasFocus) {
            if (value == null) return;
            String trimmed = StringUtil.first(value, 50, true);
            if (value.equals(defaultChangelist)) {
              append(trimmed, selected ? SELECTED_BOLD_ATTRIBUTES : BOLD_ATTRIBUTES);
            }
            else {
              append(trimmed, selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
            }
          }
        });

        break;
      }
    }
    myUsingVcsRadioButton.setEnabled(hasVcs);
  }

  @Override
  protected boolean isToBeShown() {
    ReadonlyStatusHandlerImpl.State state = ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject)).getState();
    return state != null && state.SHOW_DIALOG;
  }

  @Override
  protected void setToBeShown(boolean value, boolean onOk) {
    if (onOk) {
      ReadonlyStatusHandlerImpl.State state = ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject)).getState();
      if (state != null) {
        state.SHOW_DIALOG = value;
      }
    }
  }

  @Override
  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "vcs.readOnlyHandler.ReadOnlyStatusDialog";
  }

  @Override
  protected void doOKAction() {
    for (FileInfo info : myFiles) {
      if (myUsingFileSystemRadioButton.isSelected()) {
        info.getHandleType().selectFirst();
      }
      else if (info.hasVersionControl()) {
        info.getHandleType().select(info.getHandleType().get(1));
      }
    }

    List<FileInfo> files = new ArrayList<>();
    Collections.addAll(files, myFiles);
    String changelist = (String)myChangelist.getSelectedItem();
    ReadonlyStatusHandlerImpl.processFiles(files, changelist);

    if (files.isEmpty()) {
      super.doOKAction();
    }
    else {
      String list = StringUtil.join(files, info -> info.getFile().getPresentableUrl(), "<br>");
      String message = VcsBundle.message("handle.ro.file.status.failed", list);
      Messages.showErrorDialog(getRootPane(), message, VcsBundle.message("dialog.title.clear.read.only.file.status"));
      myFiles = files.toArray(new FileInfo[files.size()]);
      initFileList();
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final JRootPane pane = getRootPane();
    return pane != null ? pane.getDefaultButton() : null;
  }
  
  public static Dimension getDialogPreferredSize() {
    return new Dimension(500, 400);
  }

  @NotNull
  public static String getTheseFilesMessage(Collection<VirtualFile> files) {
    boolean dirsOnly = true;
    for (VirtualFile each : files) {
      if (!each.isDirectory()) {
        dirsOnly = false;
        break;
        
      }
    }
    
    int size = files.size();
    return StringUtil.pluralize("this", size) + " " + StringUtil.pluralize((dirsOnly ? "directory" : "file"), size);
  }
}