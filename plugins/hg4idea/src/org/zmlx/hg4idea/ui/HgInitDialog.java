// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The HgInitDialog appears when user wants to create new Mercurial repository, in response to the
 * {@link org.zmlx.hg4idea.action.HgInit} action.
 * It provides two options - create repository for the whole project or select a directory for the repository.
 * Also if the project directory already is a mercurial root, then no options are provided.
 * Instead a file chooser appears to select directory for the repository.
 *
 * @see org.zmlx.hg4idea.action.HgInit
 * @author Kirill Likhodedov
 */
public class HgInitDialog extends DialogWrapper {
  private JPanel contentPane;
  private JRadioButton myCreateRepositoryForTheRadioButton;
  private JRadioButton mySelectWhereToCreateRadioButton;
  private TextFieldWithBrowseButton myTextFieldBrowser;

  @Nullable private final Project myProject;
  private final boolean myShowDialog; // basing on this field, show options or invoke file chooser at once
  private final FileChooserDescriptor myFileDescriptor;
  private VirtualFile mySelectedDir;

  public HgInitDialog(@Nullable Project project) {
    super(project);
    myProject = project;
    // a file chooser instead of dialog will be shown immediately if there is no current project or if current project is already an hg root
    myShowDialog = (myProject != null && (! myProject.isDefault()) && !HgUtil.isHgRoot(myProject.getBaseDir()));

    myFileDescriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile @NotNull [] files) throws Exception {
        if (HgUtil.isHgRoot(files[0])) {
          throw new ConfigurationException(HgBundle.message("hg4idea.init.this.is.hg.root", files[0].getPresentableUrl()));
        }
        updateEverything();
      }
    };
    myFileDescriptor.setHideIgnored(false);

    init();
  }

  @Override
  protected void init() {
    super.init();
    setTitle(HgBundle.message("hg4idea.init.dialog.title"));
    if (myProject != null && (! myProject.isDefault())) {
      mySelectedDir = myProject.getBaseDir();
    }

    mySelectWhereToCreateRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTextFieldBrowser.setEnabled(true);
        updateEverything();
      }
    });
    myCreateRepositoryForTheRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTextFieldBrowser.setEnabled(false);
        updateEverything();
      }
    });
    myTextFieldBrowser.getTextField().addCaretListener(new CaretListener() {
      @Override
      public void caretUpdate(CaretEvent e) {
        updateEverything();
      }
    });

    myTextFieldBrowser.addBrowseFolderListener(HgBundle.message("hg4idea.init.destination.directory.title"),
                                               HgBundle.message("hg4idea.init.destination.directory.description"),
                                               myProject, myFileDescriptor);
  }

  /**
   * Show the dialog OR show a FileChooser to select target directory.
   */
  @Override
  public void show() {
    if (myShowDialog) {
      super.show();
    }
    else {
      mySelectedDir = FileChooser.chooseFile(myFileDescriptor, myProject, null);
    }
  }

  @Override
  public boolean isOK() {
    return myShowDialog ? super.isOK() : mySelectedDir != null;
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.create.mercurial.repository";
  }

  @Nullable
  public VirtualFile getSelectedFolder() {
    return mySelectedDir;
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  /**
   * Based on the selected option and entered path to the target directory,
   * enable/disable the 'OK' button, show error text and update mySelectedDir.
   */
  private void updateEverything() {
    if (myShowDialog && myCreateRepositoryForTheRadioButton.isSelected()) {
      enableOKAction();
      mySelectedDir = myProject.getBaseDir();
    } else {
      final VirtualFile vf = VcsUtil.getVirtualFile(myTextFieldBrowser.getText());
      if (vf == null) {
        disableOKAction();
        mySelectedDir = null;
        return;
      }
      vf.refresh(false, false);
      if (vf.exists() && vf.isValid() && vf.isDirectory()) {
        enableOKAction();
        mySelectedDir = vf;
      } else {
        disableOKAction();
        mySelectedDir = null;
      }
    }
  }

  private void enableOKAction() {
    setErrorText(null);
    setOKActionEnabled(true);
  }

  private void disableOKAction() {
    setErrorText(HgBundle.message("hg4idea.init.dialog.incorrect.path"), myTextFieldBrowser);
    setOKActionEnabled(false);
  }

}
