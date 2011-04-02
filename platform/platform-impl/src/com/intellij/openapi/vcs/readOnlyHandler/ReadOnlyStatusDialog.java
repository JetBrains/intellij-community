
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.util.ui.OptionsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author yole
 */
public class ReadOnlyStatusDialog extends OptionsDialog {
  private JPanel myTopPanel;
  private JList myFileList;
  private JRadioButton myUsingFileSystemRadioButton;
  private JRadioButton myUsingVcsRadioButton;
  private FileInfo[] myFiles;

  public ReadOnlyStatusDialog(Project project, final FileInfo[] files) {
    super(project);
    myFiles = files;
    initFileList();
    if (myUsingVcsRadioButton.isEnabled()) {
      myUsingVcsRadioButton.setSelected(true);
    }
    else {
      myUsingFileSystemRadioButton.setSelected(true);
    }
    myFileList.setCellRenderer(new FileListRenderer());
    setTitle(VcsBundle.message("dialog.title.clear.read.only.file.status"));

    init();
  }

  @Override
  public long getTypeAheadTimeoutMs() {
    return Registry.intValue("actionSystem.typeAheadTimeBeforeDialog");
  }

  private void initFileList() {
    myFileList.setModel(new AbstractListModel() {
      public int getSize() {
        return myFiles.length;
      }

      public Object getElementAt(final int index) {
        return myFiles [index].getFile();
      }
    });

    boolean hasVcs = false;
    for(FileInfo info: myFiles) {
      if (info.hasVersionControl()) {
        hasVcs = true;
        break;
      }
    }
    myUsingVcsRadioButton.setEnabled(hasVcs);    
  }

  protected boolean isToBeShown() {
    return ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandlerImpl.getInstance(myProject)).getState().SHOW_DIALOG;
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    if (onOk) {
      ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandlerImpl.getInstance(myProject)).getState().SHOW_DIALOG = value;
    }
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "vcs.readOnlyHandler.ReadOnlyStatusDialog";
  }

  protected void doOKAction() {
    for(FileInfo info: myFiles) {
      if (myUsingFileSystemRadioButton.isSelected()) {
        info.getHandleType().selectFirst();
      }
      else if (info.hasVersionControl()) {
        info.getHandleType().select(info.getHandleType().get(1));
      }
    }

    ArrayList<FileInfo> files = new ArrayList<FileInfo>();
    Collections.addAll(files, myFiles);
    ReadonlyStatusHandlerImpl.processFiles(files);

    if (files.isEmpty()) {
      super.doOKAction();
    }
    else {
      myFiles = files.toArray(new FileInfo[files.size()]);
      initFileList();
    }
  }

  @Override @Nullable
  public JComponent getPreferredFocusedComponent() {
    final JRootPane pane = getRootPane();
    return pane != null ? pane.getDefaultButton() : null;
  }

}