// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiType;
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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
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

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceConstantDialog extends DialogWrapper
  implements GrIntroduceConstantSettings, GrIntroduceDialog<GrIntroduceConstantSettings> {

  private static final Logger LOG = Logger.getInstance(GrIntroduceConstantDialog.class);

  private final GrIntroduceContext myContext;
  private final JLabel myNameLabel;
  private final JCheckBox myReplaceAllOccurrences;
  private final JPanel myPanel;
  private final GrTypeComboBox myTypeCombo;
  private ReferenceEditorComboWithBrowseButton myTargetClassEditor;
  private final NameSuggestionsField myNameField;
  private final JavaVisibilityPanel myJavaVisibilityPanel;
  private final JPanel myTargetClassPanel;
  private final JLabel myTargetClassLabel;
  private @Nullable PsiClass myTargetClass;
  private final @Nullable PsiClass myDefaultTargetClass;

  private TargetClassInfo myTargetClassInfo;

  public GrIntroduceConstantDialog(GrIntroduceContext context, @Nullable PsiClass defaultTargetClass) {
    super(context.getProject());
    myContext = context;
    myTargetClass = defaultTargetClass;
    myDefaultTargetClass = defaultTargetClass;
    {
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
        names.addAll(suggestNames());
      }

      myNameField = new NameSuggestionsField(ArrayUtilRt.toStringArray(names), myContext.getProject(), GroovyFileType.GROOVY_FILE_TYPE);

      GrTypeComboBox.registerUpDownHint(myNameField, myTypeCombo);
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "type.label"));
      panel1.add(label1,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      panel1.add(myTypeCombo, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                  0, false));
      myNameLabel = new JLabel();
      this.$$$loadLabelText$$$(myNameLabel, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "name.label"));
      panel1.add(myNameLabel,
                 new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(35, 49), null, 0, false));
      panel1.add(myNameField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(panel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      panel2.add(myJavaVisibilityPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
      myReplaceAllOccurrences = new JCheckBox();
      this.$$$loadButtonText$$$(myReplaceAllOccurrences,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "replace.all.occurrences.checkbox"));
      myPanel.add(myReplaceAllOccurrences, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myTargetClassPanel = new JPanel();
      myTargetClassPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(myTargetClassPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
      myTargetClassLabel = new JLabel();
      this.$$$loadLabelText$$$(myTargetClassLabel,
                               this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "introduce.constant.class.label"));
      myTargetClassPanel.add(myTargetClassLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
      label1.setLabelFor(myTypeCombo);
    }

    setTitle(GroovyBundle.message("introduce.constant.title"));

    myJavaVisibilityPanel.setVisibility(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY);

    updateVisibilityPanel();
    updateOkStatus();
    init();
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return myPanel; }

  public static @Nullable PsiClass getParentClass(PsiElement occurrence) {
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
                                                GlobalSearchScope.projectScope(myContext.getProject()),
                                                aClass -> aClass.getParent() instanceof GroovyFile ||
                                                          aClass.hasModifierProperty(PsiModifier.STATIC), null);
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

    myTargetClassEditor.getChildComponent().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
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
        IdeFocusManager.getGlobalInstance()
          .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myNameField, true));
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    myNameField.addDataChangedListener(() -> updateOkStatus());
  }

  @Override
  public String getVisibilityModifier() {
    return myJavaVisibilityPanel.getVisibility();
  }

  @Override
  public @Nullable PsiClass getTargetClass() {
    return myTargetClassInfo.getTargetClass();
  }

  public @NotNull String getTargetClassName() {
    return myTargetClassEditor.getText();
  }

  @Override
  public GrIntroduceConstantSettings getSettings() {
    return this;
  }

  @Override
  public @NotNull LinkedHashSet<String> suggestNames() {
    return new GrFieldNameSuggester(myContext, new GroovyVariableValidator(myContext), true).suggestNames();
  }

  @Override
  public @Nullable String getName() {
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

  private static final @NonNls String RECENTS_KEY = "GrIntroduceConstantDialog.RECENTS_KEY";

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
      final Set<String> visible = new HashSet<>();
      visible.add(PsiModifier.PRIVATE);
      visible.add(PsiModifier.PROTECTED);
      visible.add(PsiModifier.PACKAGE_LOCAL);
      visible.add(PsiModifier.PUBLIC);
      for (PsiElement occurrence : myContext.getOccurrences()) {
        final PsiManager psiManager = PsiManager.getInstance(myContext.getProject());
        for (Iterator<String> iterator = visible.iterator(); iterator.hasNext(); ) {
          String modifier = iterator.next();

          try {
            final String modifierText = PsiModifier.PACKAGE_LOCAL.equals(modifier) ? "" : modifier + " ";
            final PsiField field = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory()
              .createFieldFromText(modifierText + "int xxx;", myTargetClass);
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

    if (myDefaultTargetClass == null ||
        !targetClassName.isEmpty() && !Comparing.strEqual(targetClassName, myDefaultTargetClass.getQualifiedName())) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(myContext.getPlace());
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myContext.getProject());
      PsiClass newClass = facade.findClass(targetClassName, GlobalSearchScope.projectScope(myContext.getProject()));

      if (newClass == null &&
          Messages.showOkCancelDialog(myContext.getProject(), GroovyRefactoringBundle.message("class.does.not.exist.in.the.module"),
                                      IntroduceConstantHandler.getRefactoringNameText(), Messages.getErrorIcon()) != Messages.OK) {
        return;
      }
      myTargetClassInfo = new TargetClassInfo(targetClassName, myContext.getPlace().getContainingFile().getContainingDirectory(), module,
                                              myContext.getProject());
    }
    else {
      myTargetClassInfo = new TargetClassInfo(myDefaultTargetClass);
    }


    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getVisibilityModifier();

    RecentsManager.getInstance(myContext.getProject()).registerRecentEntry(RECENTS_KEY, targetClassName);

    super.doOKAction();
  }

  private static final class TargetClassInfo {
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

    public @Nullable PsiClass getTargetClass() {
      if (myTargetClass == null) {
        myTargetClass = getTargetClass(myQualifiedName, myBaseDirectory, myProject, myModule);
      }
      return myTargetClass;
    }

    private static @Nullable PsiClass getTargetClass(String qualifiedName, PsiDirectory baseDirectory, Project project, Module module) {
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
      return WriteAction.compute(() -> {
        final GroovyFile file =
          (GroovyFile)GroovyTemplatesFactory.createFromTemplate(psiDirectory, shortName, fileName, GroovyTemplates.GROOVY_CLASS, true);
        return file.getTypeDefinitions()[0];
      });
    }
  }
}
