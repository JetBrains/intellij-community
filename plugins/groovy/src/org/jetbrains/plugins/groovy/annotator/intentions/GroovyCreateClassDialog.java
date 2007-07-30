package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.CommonBundle;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class GroovyCreateClassDialog extends DialogWrapper {
  private JPanel contentPane;
  private JLabel myInformationLabel;
  private MySizeTextField myPackageTextField;
  private JButton myPackageChooseButton;
  private JButton buttonOK;
  private PsiDirectory myTargetDirectory;
  private Project myProject;
  private String myClassName;
  private Module myModule;

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
    getRootPane().setDefaultButton(buttonOK);

    myInformationLabel.setText(GroovyInspectionBundle.message("dialog.create.class.label.0", targetClassName));
    myPackageTextField.setText(targetPackageName != null ? targetPackageName : "");

    init();

    myPackageChooseButton.addActionListener(new ActionListener() {
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
    myPackageTextField = new MySizeTextField();
    myPackageChooseButton = new FixedSizeButton(myPackageTextField);
  }

  public static class MySizeTextField extends JTextField {
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      FontMetrics fontMetrics = getFontMetrics(getFont());
      size.width = fontMetrics.charWidth('a') * 40;
      return size;
    }
  }

  @Nullable
  protected JComponent createCenterPanel() {

    myPackageTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        getOKAction().setEnabled(PsiManager.getInstance(myProject).getNameHelper().isIdentifier(myPackageTextField.getText()));
      }
    });

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        myPackageChooseButton.doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)), myPackageTextField);

    return contentPane;
  }

  public JComponent getContentPane() {
    return contentPane;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myPackageTextField;
  }

  private String getPackageName() {
    String name = myPackageTextField.getText();
    return name != null ? name.trim() : "";
  }

  protected void doOKAction() {
    final String packageName = getPackageName();

    final Ref<String> errorStringRef = new Ref<String>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
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
      }
    }, GroovyInspectionBundle.message("create.directory.command"), null);

    if (errorStringRef.get() != null) {
      if (errorStringRef.get().length() > 0) {
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
