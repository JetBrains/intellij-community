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
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.ide.util.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GrFieldNameSuggester;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyVariableValidator;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

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
  private NameSuggestionsField myNameField;
  private JavaVisibilityPanel myJavaVisibilityPanel;
  private JPanel myTargetClassPanel;
  private JLabel myTargetClassLabel;
  @Nullable private PsiClass myTargetClass;
  @Nullable private final PsiClass myDefaultTargetClass;

  private TargetClassInfo myTargetClassInfo;

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

  @Nullable
  public static PsiClass getParentClass(PsiElement occurrence) {
    PsiElement cur = occurrence;
    while (true) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(cur, PsiClass.class, true);
      if (parentClass == null || parentClass.hasModifierProperty(PsiModifier.STATIC)) return parentClass;
      cur = parentClass;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    initializeName();
    initializeTargetClassEditor();

    if (GrIntroduceHandlerBase.resolveLocalVar(myContext) != null) {
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
                @Override
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
    Set<String> possibleClassNames = new LinkedHashSet<>();
    for (final PsiElement occurrence : myContext.getOccurrences()) {
      final PsiClass parentClass = getParentClass(occurrence);
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
      @Override
      public void documentChanged(DocumentEvent e) {
        targetClassChanged();
        updateOkStatus();
       // enableEnumDependant(introduceEnumConstant());
      }
    });
  }

  private void initializeName() {
    myNameLabel.setLabelFor(myNameField);

    myPanel.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myNameField.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    myNameField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      @Override
      public void dataChanged() {
        updateOkStatus();
      }
    });
  }

  @Override
  public String getVisibilityModifier() {
    return myJavaVisibilityPanel.getVisibility();
  }

  @Nullable
  @Override
  public PsiClass getTargetClass() {
    return myTargetClassInfo.getTargetClass();
  }

  @NotNull
  public String getTargetClassName() {
    return myTargetClassEditor.getText();
  }

  @Override
  public GrIntroduceConstantSettings getSettings() {
    return this;
  }

  @NotNull
  @Override
  public LinkedHashSet<String> suggestNames() {
    return new GrFieldNameSuggester(myContext, new GroovyVariableValidator(myContext), true).suggestNames();
  }

  @Nullable
  @Override
  public String getName() {
    return myNameField.getEnteredName();
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

    final GrVariable var = myContext.getVar();
    final GrExpression expression = myContext.getExpression();
    final StringPartInfo stringPart = myContext.getStringPart();
    if (expression != null) {
      myTypeCombo = GrTypeComboBox.createTypeComboBoxFromExpression(expression);
    }
    else if (stringPart != null) {
      myTypeCombo = GrTypeComboBox.createTypeComboBoxFromExpression(stringPart.getLiteral());
    }
    else {
      assert var != null;
      myTypeCombo = GrTypeComboBox.createTypeComboBoxWithDefType(var.getDeclaredType(), var);
    }

    List<String> names = new ArrayList<>();
    if (var != null) {
      names.add(var.getName());
    }
    if (expression != null) {
      ContainerUtil.addAll(names, suggestNames());
    }

    myNameField = new NameSuggestionsField(ArrayUtil.toStringArray(names), myContext.getProject(), GroovyFileType.GROOVY_FILE_TYPE);

    GrTypeComboBox.registerUpDownHint(myNameField, myTypeCombo);
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
      final Set<String> visible = new THashSet<>();
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
    if (myTargetClassEditor == null) return; //dialog is not initialized yet

    String text = getName();
    if (!GroovyNamesUtil.isIdentifier(text)) {
      setOKActionEnabled(false);
      return;
    }

    final String targetClassName = myTargetClassEditor.getText();
    if (targetClassName.trim().isEmpty() && myDefaultTargetClass == null) {
      setOKActionEnabled(false);
      return;
    }
    final String trimmed = targetClassName.trim();
    if (!PsiNameHelper.getInstance(myContext.getProject()).isQualifiedName(trimmed)) {
      setOKActionEnabled(false);
      return;
    }
    setOKActionEnabled(true);
  }

  @Override
  protected void doOKAction() {
    final String targetClassName = getTargetClassName();

    if (myDefaultTargetClass == null || !targetClassName.isEmpty() && !Comparing.strEqual(targetClassName, myDefaultTargetClass.getQualifiedName())) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(myContext.getPlace());
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myContext.getProject());
      PsiClass newClass = facade.findClass(targetClassName, GlobalSearchScope.projectScope(myContext.getProject()));

      if (newClass == null &&
          Messages.showOkCancelDialog(myContext.getProject(), GroovyRefactoringBundle.message("class.does.not.exist.in.the.module"),
                                      IntroduceConstantHandler.REFACTORING_NAME, Messages.getErrorIcon()) != Messages.OK) {
        return;
      }
      myTargetClassInfo = new TargetClassInfo(targetClassName, myContext.getPlace().getContainingFile().getContainingDirectory(), module, myContext.getProject());
    }
    else {
      myTargetClassInfo = new TargetClassInfo(myDefaultTargetClass);
    }


    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getVisibilityModifier();

    RecentsManager.getInstance(myContext.getProject()).registerRecentEntry(RECENTS_KEY, targetClassName);

    super.doOKAction();
  }

  private static class TargetClassInfo {
    private PsiClass myTargetClass;

    String myQualifiedName;
    PsiDirectory myBaseDirectory;
    Module myModule;
    Project myProject;

    private TargetClassInfo(PsiClass targetClass) {
      myTargetClass = targetClass;
    }

    private TargetClassInfo(String qualifiedName, PsiDirectory baseDirectory, Module module, Project project) {
      myQualifiedName = qualifiedName;
      myBaseDirectory = baseDirectory;
      myModule = module;
      myProject = project;
    }

    @Nullable
    public PsiClass getTargetClass() {
      if (myTargetClass == null) {
        myTargetClass = getTargetClass(myQualifiedName, myBaseDirectory, myProject, myModule);
      }
      return myTargetClass;
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
          .chooseDirectory(directories, null, project, new HashMap<>()) : directories[0];
      }
      else {
        psiDirectory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, baseDirectory, false);
      }
      if (psiDirectory == null) return null;
      final String shortName = StringUtil.getShortName(qualifiedName);
      final String fileName = shortName + NewGroovyActionBase.GROOVY_EXTENSION;
      final AccessToken lock = ApplicationManager.getApplication().acquireWriteActionLock(GrIntroduceConstantDialog.class);
      try {
        final GroovyFile file =
          (GroovyFile)GroovyTemplatesFactory.createFromTemplate(psiDirectory, shortName, fileName, GroovyTemplates.GROOVY_CLASS, true);
        return file.getTypeDefinitions()[0];
      }
      finally {
        lock.finish();
      }
    }
  }
}
