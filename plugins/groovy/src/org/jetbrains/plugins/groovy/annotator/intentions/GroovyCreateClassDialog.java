/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.CommonBundle;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class GroovyCreateClassDialog extends DialogWrapper {
  private JPanel myContentPane;
  private JLabel myInformationLabel;
  private EditorTextField myPackageTextField;
  private JButton myPackageChooseButton;
  private PsiDirectory myTargetDirectory;
  private final Project myProject;
  private final String myClassName;
  private final Module myModule;

  public GroovyCreateClassDialog(Project project,
                                 String title,
                                 String targetClassName,
                                 String targetPackageName,
                                 Module module) {
    super(project, true);
    myClassName = targetClassName;
    myProject = project;
    myModule = module;
    setModal(true);
    setTitle(title);

    myInformationLabel.setText(title);
    myPackageTextField.setText(targetPackageName != null ? targetPackageName : "");

    init();

    myPackageChooseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        PackageChooserDialog chooser = new PackageChooserDialog(GroovyInspectionBundle.message("dialog.create.class.package.chooser.title"), myProject);
        chooser.selectPackage(myPackageTextField.getText());
        chooser.show();
        PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          myPackageTextField.setText(aPackage.getQualifiedName());
        }
      }
    });
  }

  private void createUIComponents() {
    myPackageTextField = new EditorTextField();
    myPackageChooseButton = new FixedSizeButton(myPackageTextField);
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    myPackageTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        PsiNameHelper nameHelper = PsiNameHelper.getInstance(myProject);
        String packageName = getPackageName();
        getOKAction().setEnabled(nameHelper.isQualifiedName(packageName) || packageName != null && packageName.isEmpty());
      }
    });

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myPackageChooseButton.doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)), myPackageTextField);

    return myContentPane;
  }

  @Override
  public JComponent getContentPane() {
    return myContentPane;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPackageTextField;
  }

  private String getPackageName() {
    String name = myPackageTextField.getText();
    return name != null ? name.trim() : "";
  }

  @Override
  protected void doOKAction() {
    final String packageName = getPackageName();

    final Ref<String> errorStringRef = new Ref<>();
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      try {
        final PsiDirectory baseDir = myModule == null ? null : PackageUtil.findPossiblePackageDirectoryInModule(myModule, packageName);
        myTargetDirectory = myModule == null ? null
            : PackageUtil.findOrCreateDirectoryForPackage(myModule, packageName, baseDir, true);
        if (myTargetDirectory == null) {
          errorStringRef.set("");
          return;
        }
        errorStringRef.set(RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getClassName()));
      }
      catch (IncorrectOperationException e) {
        errorStringRef.set(e.getMessage());
      }
    }, GroovyInspectionBundle.message("create.directory.command"), null);

    if (errorStringRef.get() != null) {
      if (!errorStringRef.get().isEmpty()) {
        Messages.showMessageDialog(myProject, errorStringRef.get(), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      }
      return;
    }
    super.doOKAction();
  }

  public String getClassName() {
    return myClassName;
  }


}
