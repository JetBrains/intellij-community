// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.list.TargetPopup;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public final class ReadOnlyStatusDialog extends OptionsDialog {
  private static final SimpleTextAttributes BOLD_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.foreground());
  private static final SimpleTextAttributes SELECTED_BOLD_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.lazy(() -> NamedColorUtil.getListSelectionForeground(true)));

  private JPanel myTopPanel;
  private JList<PresentableFileInfo> myFileList;
  private JRadioButton myUsingFileSystemRadioButton;
  private JRadioButton myUsingVcsRadioButton;
  private JComboBox<String> myChangelist;
  private List<PresentableFileInfo> myFiles;

  public ReadOnlyStatusDialog(Project project, @NotNull List<PresentableFileInfo> files) {
    super(project);
    setTitle(IdeBundle.message("dialog.title.clear.read.only.file.status"));
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
    myFileList.setCellRenderer(TargetPopup.createTargetPresentationRenderer(PresentableFileInfo::getPresentation));
    init();
  }

  private void initFileList() {
    myFileList.setModel(new AbstractListModel<>() {
      @Override
      public int getSize() {
        return myFiles.size();
      }

      @Override
      public PresentableFileInfo getElementAt(final int index) {
        return myFiles.get(index);
      }
    });

    boolean hasVcs = false;
    for (FileInfo info : myFiles) {
      if (info.hasVersionControl()) {
        hasVcs = true;
        HandleType handleType = info.getSelectedHandleType();
        List<String> changelists = handleType.getChangelists();
        String defaultChangelist = handleType.getDefaultChangelist();
        myChangelist.setModel(new CollectionComboBoxModel<>(changelists, defaultChangelist));

        myChangelist.setRenderer(new ColoredListCellRenderer<>() {
          @Override
          protected void customizeCellRenderer(@NotNull JList<? extends String> list,
                                               @NlsSafe String value,
                                               int index,
                                               boolean selected,
                                               boolean hasFocus) {
            if (value == null) return;
            String trimmed = StringUtil.first(value, 50, true);
            if (value.equals(defaultChangelist)) {
              append(trimmed, selected ? SELECTED_BOLD_ATTRIBUTES : BOLD_ATTRIBUTES);
            }
            else {
              append(trimmed,
                     selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
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
    return state.SHOW_DIALOG;
  }

  @Override
  protected void setToBeShown(boolean value, boolean onOk) {
    if (onOk) {
      ReadonlyStatusHandlerImpl.State state = ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject)).getState();
      state.SHOW_DIALOG = value;
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

    List<PresentableFileInfo> files = new ArrayList<>(myFiles);
    String changelist = (String)myChangelist.getSelectedItem();
    ReadonlyStatusHandlerImpl.processFiles(files, changelist);

    if (files.isEmpty()) {
      super.doOKAction();
    }
    else {
      String list = StringUtil.join(files, info -> info.getFile().getPresentableUrl(), "<br>");
      String message = IdeBundle.message("handle.ro.file.status.failed", list);
      Messages.showErrorDialog(getRootPane(), message, IdeBundle.message("dialog.title.clear.read.only.file.status"));
      myFiles = files;
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
}