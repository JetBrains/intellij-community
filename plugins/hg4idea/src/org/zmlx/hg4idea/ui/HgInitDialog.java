/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.HgVcsMessages;

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
  private final Project myProject;
  private VirtualFile mySelectedDir;
  private FileChooserDescriptor myFileDescriptor;
  private boolean myIsProjectBaseDirHgRoot; // basing on this field, show options or invoke file chooser at once

  public HgInitDialog(Project project) {
    super(project);
    myProject = project;
    myIsProjectBaseDirHgRoot = HgUtil.isHgRoot(myProject.getBaseDir());
    init();
  }

  @Override
  protected void init() {
    super.init();
    setTitle(HgVcsMessages.message("hg4idea.init.dialog.title"));
    mySelectedDir = myProject.getBaseDir();

    mySelectWhereToCreateRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTextFieldBrowser.setEnabled(true);
        updateEverything();
      }
    });
    myCreateRepositoryForTheRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTextFieldBrowser.setEnabled(false);
        updateEverything();
      }
    });
    myTextFieldBrowser.getTextField().addCaretListener(new CaretListener() {
      public void caretUpdate(CaretEvent e) {
        updateEverything();
      }
    });

    myFileDescriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        if (HgUtil.isHgRoot(files[0])) {
          throw new ConfigurationException(HgVcsMessages.message("hg4idea.init.this.is.hg.root", files[0].getPresentableUrl()));
        }
        updateEverything();
      }
    };
    myFileDescriptor.setHideIgnored(false);
    myTextFieldBrowser.addBrowseFolderListener(HgVcsMessages.message("hg4idea.init.destination.directory.title"),
                                               HgVcsMessages.message("hg4idea.init.destination.directory.description"),
                                               myProject, myFileDescriptor);
  }

  /**
   * Show the dialog OR show a FileChooser to select target directory.
   */
  @Override
  public void show() {
    if (myIsProjectBaseDirHgRoot) {
      final VirtualFile[] files = FileChooser.chooseFiles(myProject, myFileDescriptor, myProject.getBaseDir());
      mySelectedDir = (files.length == 0 ? null : files[0]);
    } else {
      super.show();
    }
  }

  @Override
  public boolean isOK() {
    if (myIsProjectBaseDirHgRoot) {
      return mySelectedDir != null;
    }
    return super.isOK();
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
    if (myCreateRepositoryForTheRadioButton.isSelected()) {
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
    setErrorText(HgVcsMessages.message("hg4idea.init.dialog.incorrect.path"));
    setOKActionEnabled(false);
  }

}
