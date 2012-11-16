package org.jetbrains.android.refactoring;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFindStyleApplicationsDialog extends DialogWrapper {
  private JPanel myPanel;
  private JBRadioButton myModuleScopeRadio;
  private JBRadioButton myFileScopeRadio;
  private JBRadioButton myProjectScopeRadio;
  private JBLabel myCaptionLabel;

  private final VirtualFile myFile;
  private final AndroidFindStyleApplicationsProcessor myProcessor;

  private static final String FIND_STYLE_APPLICATIONS_SCOPE_PROPERTY = "ANDROID_FIND_STYLE_APPLICATION_SCOPE";

  protected AndroidFindStyleApplicationsDialog(@Nullable VirtualFile file,
                                               @NotNull AndroidFindStyleApplicationsProcessor processor,
                                               boolean showModuleRadio) {
    super(processor.getModule().getProject(), true);

    myFile = file;
    myProcessor = processor;

    final Module module = processor.getModule();
    myModuleScopeRadio.setText(AnalysisScopeBundle.message("scope.option.module.with.mnemonic", module.getName()));
    myModuleScopeRadio.setVisible(showModuleRadio);

    if (file != null) {
      myFileScopeRadio.setText("File '" + file.getName() + "'");
    }
    else {
      myFileScopeRadio.setVisible(false);
    }
    final String scopeValue = PropertiesComponent.getInstance().getValue(FIND_STYLE_APPLICATIONS_SCOPE_PROPERTY);
    AndroidFindStyleApplicationsProcessor.MyScope scope;
    try {
      scope = Enum.valueOf(AndroidFindStyleApplicationsProcessor.MyScope.class, scopeValue);
    }
    catch (IllegalArgumentException e) {
      scope = null;
    }

    if (scope == null) {
      scope = AndroidFindStyleApplicationsProcessor.MyScope.FILE;
    }

    switch (scope) {
      case PROJECT:
        myProjectScopeRadio.setSelected(true);
        break;
      case MODULE:
        myModuleScopeRadio.setSelected(true);
        break;
      case FILE:
        myFileScopeRadio.setSelected(true);
        break;
    }

    if (myModuleScopeRadio.isSelected() && !myModuleScopeRadio.isVisible() ||
        myFileScopeRadio.isSelected() && !myFileScopeRadio.isVisible()) {
      myProjectScopeRadio.setSelected(true);
    }
    myCaptionLabel.setText("Choose a scope where to search possible applications of style '" + myProcessor.getStyleName() + "'");
    setTitle(AndroidBundle.message("android.find.style.applications.title"));
    init();
  }

  @Override
  protected void doOKAction() {
    AndroidFindStyleApplicationsProcessor.MyScope scope;

    if (myModuleScopeRadio.isSelected()) {
      scope = AndroidFindStyleApplicationsProcessor.MyScope.MODULE;
    }
    else if (myProjectScopeRadio.isSelected()) {
      scope = AndroidFindStyleApplicationsProcessor.MyScope.PROJECT;
    }
    else {
      scope = AndroidFindStyleApplicationsProcessor.MyScope.FILE;
    }
    PropertiesComponent.getInstance().setValue(FIND_STYLE_APPLICATIONS_SCOPE_PROPERTY, scope.name());

    myProcessor.configureScope(scope, myFile);
    myProcessor.setPrepareSuccessfulSwingThreadCallback(new Runnable() {
      public void run() {
        close(DialogWrapper.OK_EXIT_CODE);
      }
    });
    myProcessor.run();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
