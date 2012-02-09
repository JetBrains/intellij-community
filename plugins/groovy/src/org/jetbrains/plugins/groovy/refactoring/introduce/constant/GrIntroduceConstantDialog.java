/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.ide.util.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyVariableValidator;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceConstantDialog extends DialogWrapper
  implements GrIntroduceConstantSettings, GrIntroduceDialog<GrIntroduceConstantSettings> {

  private static final Logger LOG  = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.introduce.constant.GrIntroduceConstantDialog");

  private final GrIntroduceContext myContext;
  private JLabel myNameLabel;
  private JCheckBox myReplaceAllOccurrences;
  private JPanel myPanel;
  private GrTypeComboBox myTypeCombo;
  private ReferenceEditorComboWithBrowseButton myTargetClassEditor;
  private ComboBox myNameComboBox;
  private JavaVisibilityPanel myJavaVisibilityPanel;
  private JPanel myTargetClassPanel;
  private JLabel myTargetClassLabel;
  @Nullable private PsiClass myTargetClass;
  @Nullable private PsiClass myDefaultTargetClass;

  public GrIntroduceConstantDialog(GrIntroduceContext context, @Nullable PsiClass defaultTargetClass) {
    super(context.getProject());
    myContext = context;
    myTargetClass = defaultTargetClass;
    myDefaultTargetClass = defaultTargetClass;

    setTitle(GrIntroduceConstantHandler.REFACTORING_NAME);

    myJavaVisibilityPanel.setVisibility(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY);

    updateVisibilityPanel();
    updateOkStatus();
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    initializeName();
    initializeTargetClassEditor();

    if (myContext.getVar() != null) {
      myReplaceAllOccurrences.setEnabled(false);
      myReplaceAllOccurrences.setSelected(true);
    }
    else if (myContext.getOccurrences().length < 2) {
      myReplaceAllOccurrences.setVisible(false);
    }
    return myPanel;
  }

  private void initializeTargetClassEditor() {

    myTargetClassEditor =
      new ReferenceEditorComboWithBrowseButton(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myContext.getProject())
            .createWithInnerClassesScopeChooser(RefactoringBundle.message("choose.destination.class"),
                                                GlobalSearchScope.projectScope(myContext.getProject()), new ClassFilter() {
                public boolean isAccepted(PsiClass aClass) {
                  return aClass.getParent() instanceof GroovyFile || aClass.hasModifierProperty(PsiModifier.STATIC);
                }
              }, null);
          if (myTargetClass != null) {
            chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
          }
          chooser.showDialog();
          PsiClass aClass = chooser.getSelected();
          if (aClass != null) {
            myTargetClassEditor.setText(aClass.getQualifiedName());
          }

        }
      }, "", myContext.getProject(), true, RECENTS_KEY);
    myTargetClassPanel.setLayout(new BorderLayout());
    myTargetClassPanel.add(myTargetClassLabel, BorderLayout.NORTH);
    myTargetClassPanel.add(myTargetClassEditor, BorderLayout.CENTER);
    Set<String> possibleClassNames = new LinkedHashSet<String>();
    for (final PsiElement occurrence : myContext.getOccurrences()) {
      final PsiClass parentClass = GrIntroduceConstantHandler.getParentClass(occurrence);
      if (parentClass != null && parentClass.getQualifiedName() != null) {
        possibleClassNames.add(parentClass.getQualifiedName());
      }
    }

    for (String possibleClassName : possibleClassNames) {
      myTargetClassEditor.prependItem(possibleClassName);
    }

    if (myDefaultTargetClass != null) {
      myTargetClassEditor.prependItem(myDefaultTargetClass.getQualifiedName());
    }

    myTargetClassEditor.getChildComponent().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        targetClassChanged();
        updateOkStatus();
       // enableEnumDependant(introduceEnumConstant());
      }
    });
  }

  private void initializeName() {
    myNameLabel.setLabelFor(myNameComboBox);
    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myContext.getProject(), GroovyFileType.GROOVY_FILE_TYPE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);


    myPanel.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    final GrVariable var = myContext.getVar();
    if (var != null) {
      myNameComboBox.addItem(var.getName());
    }
    String[] possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(myContext.getExpression(), new GroovyVariableValidator(myContext), true);
    for (String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }

    ((EditorTextField)myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        updateOkStatus();
      }
    });

    myNameComboBox.addItemListener(
      new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateOkStatus();
        }
      }
    );
  }

  @Override
  public String getVisibilityModifier() {
    return myJavaVisibilityPanel.getVisibility();
  }

  @Nullable
  @Override
  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  @NotNull
  public String getTargetClassName() {
    return myTargetClassEditor.getText();
  }

  @Override
  public GrIntroduceConstantSettings getSettings() {
    return this;
  }

  @Nullable
  @Override
  public String getName() {
    if (myNameComboBox.getEditor().getItem() instanceof String && ((String)myNameComboBox.getEditor().getItem()).length() > 0) {
      return (String)myNameComboBox.getEditor().getItem();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean replaceAllOccurrences() {
    return myReplaceAllOccurrences.isSelected();
  }

  @Override
  public PsiType getSelectedType() {
    return myTypeCombo.getSelectedType();
  }

  @NonNls private static final String RECENTS_KEY = "GrIntroduceConstantDialog.RECENTS_KEY";

  private void createUIComponents() {
    myJavaVisibilityPanel = new JavaVisibilityPanel(false, true);
    if (myContext.getExpression() == null) {
      myTypeCombo = GrTypeComboBox.createTypeComboBoxWithDefType(myContext.getVar().getDeclaredType()
      );
    }
    else {
      myTypeCombo = GrTypeComboBox.createTypeComboBoxFromExpression(myContext.getExpression());
    }
  }

  private void targetClassChanged() {
    final String targetClassName = getTargetClassName();
    myTargetClass =
      JavaPsiFacade.getInstance(myContext.getProject()).findClass(targetClassName, GlobalSearchScope.projectScope(myContext.getProject()));
    updateVisibilityPanel();
//    myIntroduceEnumConstantCb.setEnabled(EnumConstantsUtil.isSuitableForEnumConstant(getSelectedType(), myTargetClassEditor));
  }

  private void updateVisibilityPanel() {
    if (myTargetClass != null && myTargetClass.isInterface()) {
      myJavaVisibilityPanel.disableAllButPublic();
    }
    else {
      UIUtil.setEnabled(myJavaVisibilityPanel, true, true);
      // exclude all modifiers not visible from all occurrences
      final Set<String> visible = new THashSet<String>();
      visible.add(PsiModifier.PRIVATE);
      visible.add(PsiModifier.PROTECTED);
      visible.add(PsiModifier.PACKAGE_LOCAL);
      visible.add(PsiModifier.PUBLIC);
      for (PsiElement occurrence : myContext.getOccurrences()) {
        final PsiManager psiManager = PsiManager.getInstance(myContext.getProject());
        for (Iterator<String> iterator = visible.iterator(); iterator.hasNext();) {
          String modifier = iterator.next();

          try {
            final String modifierText = PsiModifier.PACKAGE_LOCAL.equals(modifier) ? "" : modifier + " ";
            final PsiField field = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createFieldFromText(modifierText + "int xxx;", myTargetClass);
            if (!JavaResolveUtil.isAccessible(field, myTargetClass, field.getModifierList(), occurrence, myTargetClass, null)) {
              iterator.remove();
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      if (!visible.contains(getVisibilityModifier())) {
        if (visible.contains(PsiModifier.PUBLIC)) myJavaVisibilityPanel.setVisibility(PsiModifier.PUBLIC);
        if (visible.contains(PsiModifier.PACKAGE_LOCAL)) myJavaVisibilityPanel.setVisibility(PsiModifier.PACKAGE_LOCAL);
        if (visible.contains(PsiModifier.PROTECTED)) myJavaVisibilityPanel.setVisibility(PsiModifier.PROTECTED);
        if (visible.contains(PsiModifier.PRIVATE)) myJavaVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
      }
    }
  }

  private void updateOkStatus() {
    String text = getName();
    if (!GroovyNamesUtil.isIdentifier(text)) {
      setOKActionEnabled(false);
      return;
    }

    final String targetClassName = myTargetClassEditor.getText();
    if (targetClassName.trim().length() == 0 && myDefaultTargetClass == null) {
      setOKActionEnabled(false);
      return;
    }
    final String trimmed = targetClassName.trim();
    if (!JavaPsiFacade.getInstance(myContext.getProject()).getNameHelper().isQualifiedName(trimmed)) {
      setOKActionEnabled(false);
      return;
    }
    setOKActionEnabled(true);
  }

  @Override
  protected void doOKAction() {
    final String targetClassName = getTargetClassName();
    PsiClass newClass = myDefaultTargetClass;

    if (myDefaultTargetClass == null ||
        !targetClassName.isEmpty() && !Comparing.strEqual(targetClassName, myDefaultTargetClass.getQualifiedName())) {
      final Module module = ModuleUtil.findModuleForPsiElement(myContext.getPlace());
      newClass = JavaPsiFacade.getInstance(myContext.getProject()).findClass(targetClassName, GlobalSearchScope.projectScope(
        myContext.getProject()));
      if (newClass == null) {
        if (Messages.showOkCancelDialog(myContext.getProject(), GroovyRefactoringBundle.message("class.does.not.exist.in.the.module"),
                                        IntroduceConstantHandler.REFACTORING_NAME, Messages.getErrorIcon()) != OK_EXIT_CODE) {
          return;
        }
        myTargetClass =
          getTargetClass(targetClassName, myContext.getPlace().getContainingFile().getContainingDirectory(), myContext.getProject(), module);
        if (myTargetClass == null) return;
      }
      else {
        myTargetClass =
          getTargetClass(targetClassName, myContext.getPlace().getContainingFile().getContainingDirectory(), myContext.getProject(), module);
      }
    }

    final GrIntroduceConstantHandler introduceConstantHandler = new GrIntroduceConstantHandler();
    String errorString = check();
    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(introduceConstantHandler.getRefactoringName(),
                                             RefactoringBundle.getCannotRefactorMessage(errorString), introduceConstantHandler.getHelpID(),
                                             myContext.getProject());
      return;
    }

    String fieldName = getName();
    if (newClass != null) {
      PsiField oldField = newClass.findFieldByName(fieldName, true);

      if (oldField != null) {
        int answer = Messages.showYesNoDialog(myContext.getProject(), RefactoringBundle
          .message("field.exists", fieldName, oldField.getContainingClass().getQualifiedName()),
                                              introduceConstantHandler.getRefactoringName(), Messages.getWarningIcon());
        if (answer != 0) {
          return;
        }
      }
    }

    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getVisibilityModifier();

    RecentsManager.getInstance(myContext.getProject()).registerRecentEntry(RECENTS_KEY, targetClassName);

    super.doOKAction();
  }

  @Nullable
  private String check() {
    if (myTargetClass != null && !GroovyFileType.GROOVY_LANGUAGE.equals(myTargetClass.getLanguage())) {
      return GroovyRefactoringBundle.message("class.language.is.not.groovy");
    }

    String fieldName = getName();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myContext.getProject());

    if (fieldName == null || fieldName.isEmpty()) {
      return RefactoringBundle.message("no.field.name.specified");
    }

    else if (!facade.getNameHelper().isIdentifier(fieldName)) {
      return RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }

    final String targetClassName = getTargetClassName();
    if (myDefaultTargetClass == null && targetClassName.isEmpty()) {
      return GroovyRefactoringBundle.message("target.class.is.not.specified");
    }

    if (myTargetClass instanceof GroovyScriptClass) {
      return GroovyRefactoringBundle.message("target.class.must.not.be.script");
    }

    return null;
  }

  @Nullable
  private static PsiClass getTargetClass(String qualifiedName, PsiDirectory baseDirectory, Project project, Module module) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

    PsiClass targetClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope);
    if (targetClass != null) return targetClass;

    final String packageName = StringUtil.getPackageName(qualifiedName);
    PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    final PsiDirectory psiDirectory;
    if (psiPackage != null) {
      final PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.allScope(project));
      psiDirectory = directories.length > 1 ? DirectoryChooserUtil
        .chooseDirectory(directories, null, project, new HashMap<PsiDirectory, String>()) : directories[0];
    }
    else {
      psiDirectory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, baseDirectory, false);
    }
    if (psiDirectory == null) return null;
    final String shortName = StringUtil.getShortName(qualifiedName);
    final String fileName = shortName + NewGroovyActionBase.GROOVY_EXTENSION;
    final AccessToken lock = ApplicationManager.getApplication().acquireWriteActionLock(GrIntroduceConstantDialog.class);
    try {
      final GroovyFile file = (GroovyFile)GroovyTemplatesFactory.createFromTemplate(psiDirectory, shortName, fileName, "GroovyClass.groovy");
      return file.getTypeDefinitions()[0];
    }
    finally {
      lock.finish();
    }
  }

}
