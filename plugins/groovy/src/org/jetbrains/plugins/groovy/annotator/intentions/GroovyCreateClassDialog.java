// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.CommonBundle;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

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
                                 @DialogTitle String title,
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
        PackageChooserDialog chooser = new PackageChooserDialog(GroovyBundle.message("dialog.create.class.package.chooser.title"), myProject);
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
    myPackageTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        PsiNameHelper nameHelper = PsiNameHelper.getInstance(myProject);
        String packageName = getPackageName();
        getOKAction().setEnabled(nameHelper.isQualifiedName(packageName) || packageName.isEmpty());
      }
    });

    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
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
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPackageTextField;
  }

  private @NotNull String getPackageName() {
    return myPackageTextField.getText().trim();
  }

  @Override
  protected void doOKAction() {
    final String packageName = getPackageName();

    final Ref<@DialogMessage String> errorStringRef = new Ref<>();
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
    }, GroovyBundle.message("create.directory.command"), null);

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
