/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author cdr
 */
package com.intellij.codeInspection.i18n;

import com.intellij.ExtensionPoints;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.impl.ConcatenationToMessageFormatAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class I18nInspection extends BaseLocalInspectionTool {
  public boolean ignoreForAssertStatements = true;
  public boolean ignoreForExceptionConstructors = true;
  @NonNls public String ignoreForSpecifiedExceptionConstructors = "";
  public boolean ignoreForJUnitAsserts = true;
  public boolean ignoreForClassReferences = true;
  public boolean ignoreForPropertyKeyReferences = true;
  public boolean ignoreForNonAlpha = true;
  public boolean ignoreAssignedToConstants = false;
  public boolean ignoreToString = false;
  @NonNls public String nonNlsCommentPattern = "NON-NLS";

  private static final LocalQuickFix I18N_QUICK_FIX = new I18nizeQuickFix();
  private static final I18nizeConcatenationQuickFix I18N_CONCATENATION_QUICK_FIX = new I18nizeConcatenationQuickFix();

  @Nullable private Pattern myCachedNonNlsPattern;
  @NonNls private static final String TO_STRING = "toString";

  public I18nInspection() {
    cacheNonNlsCommentPattern();
  }

  @Override
  public SuppressIntentionAction[] getSuppressActions(PsiElement element) {
    SuppressIntentionAction[] actions = {};
    if (myCachedNonNlsPattern != null) {
      actions = new SuppressIntentionAction[]{new SuppressByCommentOutAction(nonNlsCommentPattern)};
    }
    return ArrayUtil.mergeArrays(actions, super.getSuppressActions(element), SuppressIntentionAction.class);
  }

  public void readSettings(Element node) throws InvalidDataException {
    super.readSettings(node);
    cacheNonNlsCommentPattern();
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return CodeInsightBundle.message("inspection.i18n.display.name");
  }

  @NotNull
  public String getShortName() {
    return "HardCodedStringLiteral";
  }

  public JComponent createOptionsPanel() {
    final GridBagLayout layout = new GridBagLayout();
    final JPanel panel = new JPanel(layout);
    final JCheckBox assertStatementsCheckbox = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.assert"), ignoreForAssertStatements);
    assertStatementsCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForAssertStatements = assertStatementsCheckbox.isSelected();
      }
    });
    final JCheckBox exceptionConstructorCheck =
      new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.for.exception.constructor.arguments"),
                    ignoreForExceptionConstructors);
    exceptionConstructorCheck.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForExceptionConstructors = exceptionConstructorCheck.isSelected();
      }
    });

    final JTextField specifiedExceptions = new JTextField(ignoreForSpecifiedExceptionConstructors);
    specifiedExceptions.getDocument().addDocumentListener(new DocumentAdapter(){
      protected void textChanged(DocumentEvent e) {
        ignoreForSpecifiedExceptionConstructors = specifiedExceptions.getText();
      }
    });

    final JCheckBox junitAssertCheckbox = new JCheckBox(
      CodeInsightBundle.message("inspection.i18n.option.ignore.for.junit.assert.arguments"), ignoreForJUnitAsserts);
    junitAssertCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForJUnitAsserts = junitAssertCheckbox.isSelected();
      }
    });
    final JCheckBox classRef = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.qualified.class.names"), ignoreForClassReferences);
    classRef.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForClassReferences = classRef.isSelected();
      }
    });
    final JCheckBox propertyRef = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.property.keys"), ignoreForPropertyKeyReferences);
    propertyRef.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForPropertyKeyReferences = propertyRef.isSelected();
      }
    });
    final JCheckBox nonAlpha = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.nonalphanumerics"), ignoreForNonAlpha);
    nonAlpha.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForNonAlpha = nonAlpha.isSelected();
      }
    });
    final JCheckBox assignedToConstants = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.assigned.to.constants"), ignoreAssignedToConstants);
    assignedToConstants.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreAssignedToConstants = assignedToConstants.isSelected();
      }
    });
    final JCheckBox chkToString = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.tostring"), ignoreToString);
    chkToString.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreToString = chkToString.isSelected();
      }
    });

    final GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;

    gc.gridx = GridBagConstraints.REMAINDER;
    gc.gridy = 0;
    gc.weightx = 1;
    gc.weighty = 0;
    panel.add(assertStatementsCheckbox, gc);

    gc.gridy ++;
    panel.add(junitAssertCheckbox, gc);

    gc.gridy ++;
    panel.add(exceptionConstructorCheck, gc);

    gc.gridy ++;
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    panel.add(new FieldPanel(specifiedExceptions,
                             CodeInsightBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"),
                             CodeInsightBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"),
                             openProjects.length == 0 ? null :
                             new ActionListener() {
                               public void actionPerformed(ActionEvent e) {
                                 createIgnoreExceptionsConfigurationDialog(openProjects[0], specifiedExceptions).show();
                               }
                             },
                             null), gc);

    gc.gridy ++;
    panel.add(classRef, gc);

    gc.gridy ++;
    panel.add(propertyRef, gc);

    gc.gridy++;
    panel.add(assignedToConstants, gc);

    gc.gridy++;
    panel.add(chkToString, gc);

    gc.gridy ++;
    panel.add(nonAlpha, gc);

    gc.gridy ++;
    final JTextField text = new JTextField(nonNlsCommentPattern);
    final FieldPanel nonNlsCommentPatternComponent =
      new FieldPanel(text, CodeInsightBundle.message("inspection.i18n.option.ignore.comment.pattern"),
                     CodeInsightBundle.message("inspection.i18n.option.ignore.comment.title"), null, new Runnable() {
        public void run() {
          nonNlsCommentPattern = text.getText();
          cacheNonNlsCommentPattern();
        }
      });
    panel.add(nonNlsCommentPatternComponent, gc);

    gc.gridy ++;
    gc.weighty = 1;
    panel.add(new JPanel(), gc);
    return panel;
  }

  @SuppressWarnings({"NonStaticInitializer"})
  private DialogWrapper createIgnoreExceptionsConfigurationDialog(final Project project, final JTextField specifiedExceptions) {
    return new DialogWrapper(true) {
      private AddDeleteListPanel myPanel;
      {
        setTitle(CodeInsightBundle.message(
          "inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"));
        init();
      }

      protected JComponent createCenterPanel() {
        final String[] ignored = ignoreForSpecifiedExceptionConstructors.split(",");
        final List<String> initialList = new ArrayList<String>();
        if (ignored != null){
          for (String e : ignored) {
            if (e.length() > 0) initialList.add(e);
          }
        }
        myPanel = new AddDeleteListPanel(null,
                                         initialList) {
          protected Object findItemToAdd() {
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).
              createInheritanceClassChooser(
                CodeInsightBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"), scope,
                JavaPsiFacade.getInstance(project).findClass("java.lang.Throwable", scope), true, true, null);
            chooser.showDialog();
            PsiClass selectedClass = chooser.getSelectedClass();
            return selectedClass != null ? selectedClass.getQualifiedName() : null;
          }
        };
        return myPanel;
      }

      protected void doOKAction() {
        StringBuilder buf = new StringBuilder();
        final Object[] exceptions = myPanel.getListItems();
        for (Object exception : exceptions) {
          buf.append(",").append(exception);
        }
        specifiedExceptions.setText(buf.length() > 0 ? buf.substring(1) : buf.toString());
        super.doOKAction();
      }
    };
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(method.getContainingClass())) {
      return null;
    }
    final PsiCodeBlock body = method.getBody();
    if (body != null) {
      return checkElement(body, manager, isOnTheFly);
    }
    return null;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(aClass)) {
      return null;
    }
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] descriptors = checkElement(initializer, manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(result, descriptors);
      }
    }

    return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(field.getContainingClass())) {
      return null;
    }
    if (AnnotationUtil.isAnnotated(field, AnnotationUtil.NON_NLS, false)) {
      return null;
    }
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) return checkElement(initializer, manager, isOnTheFly);

    if (field instanceof PsiEnumConstant) {
      return checkElement(((PsiEnumConstant)field).getArgumentList(), manager, isOnTheFly);
    }
    return null;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    ExtensionPoint<FileCheckingInspection> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.I18N_INSPECTION_TOOL);
    final FileCheckingInspection[] fileCheckingInspections = point.getExtensions();
    for(FileCheckingInspection obj: fileCheckingInspections) {
      ProblemDescriptor[] descriptors = obj.checkFile(file, manager, isOnTheFly);
      if (descriptors != null) {
        return descriptors;
      }
    }

    return null;
  }

  private ProblemDescriptor[] checkElement(final PsiElement element, InspectionManager manager, boolean isOnTheFly) {
    StringI18nVisitor visitor = new StringI18nVisitor(manager, isOnTheFly);
    element.accept(visitor);
    List<ProblemDescriptor> problems = visitor.getProblems();
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static LocalQuickFix createIntroduceConstantFix(final PsiExpression... expressions) {
    //noinspection unchecked
    final SmartPsiElementPointer<PsiExpression>[] pointers = new SmartPsiElementPointer[expressions.length];
    for(int i=0; i<expressions.length; i++) {
      pointers [i] = SmartPointerManager.getInstance(expressions [i].getProject()).createSmartPsiElementPointer(expressions [i]);
    }
    return new LocalQuickFix() {
      @NotNull
      public String getName() {
        return IntroduceConstantHandler.REFACTORING_NAME;
      }

      public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
        List<PsiExpression> exprList = new ArrayList<PsiExpression>();
        for (SmartPsiElementPointer<PsiExpression> ptr : pointers) {
          PsiExpression expr = ptr.getElement();
          if (expr != null && expr.isValid()) {
            exprList.add(expr);
          }
        }
        new IntroduceConstantHandler().invoke(project, exprList.toArray(new PsiExpression[exprList.size()]));
      }

      @NotNull
      public String getFamilyName() {
        return getName();
      }
    };
  }

  private class StringI18nVisitor extends JavaRecursiveElementVisitor {
    private final List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();
    private final InspectionManager myManager;
    private final boolean myOnTheFly;

    public StringI18nVisitor(final InspectionManager manager, boolean onTheFly) {
      myManager = manager;
      myOnTheFly = onTheFly;
    }

    @Override public void visitAnonymousClass(PsiAnonymousClass aClass) {
      final PsiExpressionList argumentList = aClass.getArgumentList();
      if (argumentList != null) {
        argumentList.accept(this);
      }
    }

    @Override public void visitClass(PsiClass aClass) {}

    @Override public void visitField(PsiField field) {}

    @Override public void visitMethod(PsiMethod method) {}

    @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if (!(value instanceof String)) return;
      String stringValue = (String)value;
      if (stringValue.trim().length() == 0) {
        return;
      }

      Set<PsiModifierListOwner> nonNlsTargets = new HashSet<PsiModifierListOwner>();
      if (canBeI18ned(expression, stringValue, nonNlsTargets)) {
        final String description = CodeInsightBundle.message("inspection.i18n.message.general.with.value",
                                                             "#ref");
                                                             //JDOMUtil.escapeText(stringValue));

        List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
        if (ConcatenationToMessageFormatAction.getEnclosingLiteralConcatenation(expression) != null) {
          fixes.add(I18N_CONCATENATION_QUICK_FIX);
        }
        fixes.add(I18N_QUICK_FIX);

        if (!isNotConstantFieldInitializer(expression)) {
          fixes.add(createIntroduceConstantFix(expression));
        }

        final Project project = expression.getManager().getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        if (PsiUtil.getLanguageLevel(expression).hasEnumKeywordAndAutoboxing()) {
          for (PsiModifierListOwner element : nonNlsTargets) {
            if (!AnnotationUtil.isAnnotated(element, AnnotationUtil.NLS, true)) {
              if (!element.getManager().isInProject(element) || facade.findClass(AnnotationUtil.NON_NLS, element.getResolveScope()) != null) {
                fixes.add(new AddAnnotationFix(AnnotationUtil.NON_NLS, element));
              }
            }
          }
        }

        final ProblemDescriptor problem = myManager
          .createProblemDescriptor(expression,
                                   description, myOnTheFly, fixes.toArray(new LocalQuickFix[fixes.size()]), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        myProblems.add(problem);
      }
    }

    private boolean isNotConstantFieldInitializer(final PsiExpression expression) {
      PsiField parentField = expression.getParent() instanceof PsiField ? (PsiField) expression.getParent() : null;
      return parentField != null && expression == parentField.getInitializer() &&
             parentField.hasModifierProperty(PsiModifier.FINAL) &&
             parentField.hasModifierProperty(PsiModifier.STATIC);
    }


    @Override public void visitAnnotation(PsiAnnotation annotation) {
      //prevent from @SuppressWarnings
      if (!"java.lang.SuppressWarnings".equals(annotation.getQualifiedName())){
        super.visitAnnotation(annotation);
      }
    }

    public List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }

  private boolean canBeI18ned(PsiLiteralExpression expression, String value, final Set<PsiModifierListOwner> nonNlsTargets) {
    if (ignoreForNonAlpha && !StringUtil.containsAlphaCharacters(value)) {
      return false;
    }

    PsiField parentField = PsiTreeUtil.getParentOfType(expression, PsiField.class);
    if (parentField != null) {
      nonNlsTargets.add(parentField);
    }

    if (JavaI18nUtil.isPassedToAnnotatedParam(expression, AnnotationUtil.NON_NLS, new HashMap<String, Object>(), nonNlsTargets)) {
      return false;
    }

    if (isInNonNlsCall(expression, nonNlsTargets)) {
      return false;
    }

    if (isInNonNlsEquals(expression, nonNlsTargets)) {
      return false;
    }

    if (isPassedToNonNlsVariable(expression, nonNlsTargets)) {
      return false;
    }

    if (JavaI18nUtil.mustBePropertyKey(expression, new HashMap<String, Object>())) {
      return false;
    }

    if (isReturnedFromNonNlsMethod(expression, nonNlsTargets)) {
      return false;
    }
    if (ignoreForAssertStatements && isArgOfAssertStatement(expression)) {
      return false;
    }
    if (ignoreForExceptionConstructors && isArgOfExceptionConstructor(expression)) {
      return false;
    }
    if (!ignoreForExceptionConstructors && isArgOfSpecifiedExceptionConstructor(expression, ignoreForSpecifiedExceptionConstructors.split(","))) {
      return false;
    }
    if (ignoreForJUnitAsserts && isArgOfJUnitAssertion(expression)) {
      return false;
    }
    if (ignoreForClassReferences && isClassRef(expression, value)) {
      return false;
    }
    if (ignoreForPropertyKeyReferences && JavaI18nUtil.isPropertyRef(expression, value, null)) {
      return false;
    }
    if (ignoreToString && isToString(expression)) {
      return false;
    }

    Pattern pattern = myCachedNonNlsPattern;
    if (pattern != null) {
      Project project = expression.getProject();
      PsiFile file = expression.getContainingFile();
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      int line = document.getLineNumber(expression.getTextRange().getStartOffset());
      int lineStartOffset = document.getLineStartOffset(line);
      CharSequence lineText = document.getCharsSequence().subSequence(lineStartOffset, document.getLineEndOffset(line));

      Matcher matcher = pattern.matcher(lineText);
      int start = 0;
      while (matcher.find(start)) {
        start = matcher.start();
        PsiElement element = file.findElementAt(lineStartOffset + start);
        if (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) return false;
        if (start == lineText.length() - 1) break;
        start++;
      }
    }

    return true;
  }

  public void cacheNonNlsCommentPattern() {
    myCachedNonNlsPattern = nonNlsCommentPattern.trim().length() == 0 ? null : Pattern.compile(nonNlsCommentPattern);
  }

  private static boolean isClassRef(final PsiLiteralExpression expression, String value) {
    if (StringUtil.startsWithChar(value,'#')) {
      value = value.substring(1); // A favor for JetBrains team to catch common Logger usage practice.
    }

    return JavaPsiFacade.getInstance(expression.getProject()).findClass(value, GlobalSearchScope.allScope(expression.getProject())) != null;
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  private static boolean isClassNonNls(final PsiClass clazz) {
    final PsiDirectory directory = clazz.getContainingFile().getContainingDirectory();
    if (directory != null && isPackageNonNls(JavaDirectoryService.getInstance().getPackage(directory))) {
      return true;
    }

    return false;
  }

  public static boolean isPackageNonNls(final PsiPackage psiPackage) {
    if (psiPackage == null || psiPackage.getName() == null) {
      return false;
    }
    final PsiModifierList pkgModifierList = psiPackage.getAnnotationList();
    if (pkgModifierList != null && pkgModifierList.findAnnotation(AnnotationUtil.NON_NLS) != null) {
      return true;
    }
    return isPackageNonNls(psiPackage.getParentPackage());
  }

  private boolean isPassedToNonNlsVariable(final PsiLiteralExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    PsiExpression toplevel = JavaI18nUtil.getToplevelExpression(expression);
    PsiVariable var = null;
    if (toplevel instanceof PsiAssignmentExpression) {
      PsiExpression lExpression = ((PsiAssignmentExpression)toplevel).getLExpression();
      while (lExpression instanceof PsiArrayAccessExpression) {
        lExpression = ((PsiArrayAccessExpression)lExpression).getArrayExpression();
      }
      if (lExpression instanceof PsiReferenceExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
        if (resolved instanceof PsiVariable) var = (PsiVariable)resolved;
      }
    }

    if (var == null) {
      PsiElement parent = toplevel.getParent();
      if (parent instanceof PsiVariable && toplevel.equals(((PsiVariable)parent).getInitializer())) {
        var = (PsiVariable)parent;
      }
    }

    if (var != null) {
      if (annotatedAsNonNls(var)) {
        return true;
      }
      if (ignoreAssignedToConstants &&
          var.hasModifierProperty(PsiModifier.STATIC) &&
          var.hasModifierProperty(PsiModifier.FINAL)) {
        return true;
      }
      nonNlsTargets.add(var);
    }
    return false;
  }

  private static boolean annotatedAsNonNls(final PsiModifierListOwner parent) {
    if (parent instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)parent;
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)declarationScope;
        final int index = method.getParameterList().getParameterIndex(parameter);
        return JavaI18nUtil.isMethodParameterAnnotatedWith(method, index, new HashSet<PsiMethod>(), AnnotationUtil.NON_NLS,
                                                       new HashMap<String, Object>(), null);
      }
    }
    return AnnotationUtil.isAnnotated(parent, AnnotationUtil.NON_NLS, false);
  }

  private static boolean isInNonNlsEquals(PsiExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    if (!(expression.getParent().getParent() instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call = (PsiMethodCallExpression)expression.getParent().getParent();
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != expression) {
      return false;
    }
    if (!"equals".equals(methodExpression.getReferenceName())) {
      return false;
    }
    final PsiElement resolved = methodExpression.resolve();
    if (!(resolved instanceof PsiMethod)) {
      return false;
    }
    PsiType objectType = PsiType.getJavaLangObject(resolved.getManager(), resolved.getResolveScope());
    MethodSignature equalsSignature = MethodSignatureUtil.createMethodSignature("equals",
                                                                                new PsiType[]{objectType},
                                                                                PsiTypeParameter.EMPTY_ARRAY,
                                                                                PsiSubstitutor.EMPTY);
    if (!equalsSignature.equals(((PsiMethod)resolved).getSignature(PsiSubstitutor.EMPTY))) {
      return false;
    }
    final PsiExpression[] expressions = call.getArgumentList().getExpressions();
    if (expressions.length != 1) {
      return false;
    }
    final PsiExpression arg = expressions[0];
    PsiReferenceExpression ref = null;
    if (arg instanceof PsiReferenceExpression) {
      ref = (PsiReferenceExpression)arg;
    }
    else if (arg instanceof PsiMethodCallExpression) ref = ((PsiMethodCallExpression)arg).getMethodExpression();
    if (ref != null) {
      final PsiElement resolvedEntity = ref.resolve();
      if (resolvedEntity instanceof PsiModifierListOwner) {
        PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)resolvedEntity;
        if (annotatedAsNonNls(modifierListOwner)) {
          return true;
        }
        nonNlsTargets.add(modifierListOwner);
      }
    }
    return false;
  }

  private static boolean isInNonNlsCall(PsiExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    expression = JavaI18nUtil.getToplevelExpression(expression);
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpressionList) {
      final PsiElement grParent = parent.getParent();
      if (grParent instanceof PsiMethodCallExpression) {
        return isNonNlsCall((PsiMethodCallExpression)grParent, nonNlsTargets);
      }
      else if (grParent instanceof PsiNewExpression) {
        final PsiElement parentOfNew = grParent.getParent();
        if (parentOfNew instanceof PsiLocalVariable) {
          final PsiLocalVariable newVariable = (PsiLocalVariable)parentOfNew;
          if (annotatedAsNonNls(newVariable)) {
            return true;
          }
          nonNlsTargets.add(newVariable);
          return false;
        }
        else if (parentOfNew instanceof PsiAssignmentExpression) {
          final PsiExpression lExpression = ((PsiAssignmentExpression)parentOfNew).getLExpression();
          if (lExpression instanceof PsiReferenceExpression) {
            final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
            if (resolved instanceof PsiModifierListOwner) {
              final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)resolved;
              if (annotatedAsNonNls(modifierListOwner)) {
                return true;
              }
              nonNlsTargets.add(modifierListOwner);
              return false;
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isNonNlsCall(PsiMethodCallExpression grParent, Set<PsiModifierListOwner> nonNlsTargets) {
    final PsiReferenceExpression methodExpression = grParent.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)qualifier).resolve();
      if (resolved instanceof PsiModifierListOwner) {
        final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)resolved;
        if (annotatedAsNonNls(modifierListOwner)) {
          return true;
        }
        nonNlsTargets.add(modifierListOwner);
        return false;
      }
    } else if (qualifier instanceof PsiMethodCallExpression) {
      final PsiType type = qualifier.getType();
      if (type != null && type.equals(methodExpression.getType())) {
        return isNonNlsCall((PsiMethodCallExpression)qualifier, nonNlsTargets);
      }
    }
    return false;
  }

  private static boolean isReturnedFromNonNlsMethod(final PsiLiteralExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    final PsiElement returnStmt = PsiTreeUtil.getParentOfType(expression,
                                                              PsiReturnStatement.class, PsiMethodCallExpression.class);
    if (returnStmt == null || !(returnStmt instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (method == null) return false;

    if (AnnotationUtil.isAnnotated(method, AnnotationUtil.NON_NLS, true)) {
      return true;
    }
    nonNlsTargets.add(method);
    return false;
  }

  private static boolean isToString(final PsiLiteralExpression expression) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (method == null) return false;
    final PsiType returnType = method.getReturnType();
    return TO_STRING.equals(method.getName())
           && method.getParameterList().getParametersCount() == 0
           && returnType != null
           && "java.lang.String".equals(returnType.getCanonicalText());
  }

  private static boolean isArgOfJUnitAssertion(PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call = (PsiMethodCallExpression)grandparent;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (methodName == null) {
      return false;
    }

    if (!methodName.startsWith("assert") && !methodName.equals("fail")) {
      return false;
    }
    final PsiMethod method = call.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final Project project = expression.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass junitAssert = JavaPsiFacade.getInstance(project).findClass("junit.framework.Assert", scope);
    return junitAssert != null && !containingClass.isInheritor(junitAssert, true);
  }

  private static boolean isArgOfExceptionConstructor(PsiExpression expression) {
    final PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, PsiClass.class);
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof PsiNewExpression)) {
      return false;
    }
    final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)grandparent).getClassReference();
    if (reference == null) {
      return false;
    }
    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)referent;
    final Project project = expression.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass throwable = JavaPsiFacade.getInstance(project).findClass("java.lang.Throwable", scope);
    return throwable != null && aClass.isInheritor(throwable, true);
  }

  private static boolean isArgOfSpecifiedExceptionConstructor(PsiExpression expression, String[] specifiedExceptions) {
    if (specifiedExceptions.length == 0) return false;

    final PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, PsiClass.class);
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof PsiNewExpression)) {
      return false;
    }
    final PsiJavaCodeReferenceElement reference =
      ((PsiNewExpression)grandparent).getClassReference();
    if (reference == null) {
      return false;
    }
    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)referent;

    for (String specifiedException : specifiedExceptions) {
      if (specifiedException.equals(aClass.getQualifiedName())) return true;

    }

    return false;
  }

  private static boolean isArgOfAssertStatement(PsiExpression expression) {
    return PsiTreeUtil.getParentOfType(expression, PsiAssertStatement.class, PsiClass.class) instanceof PsiAssertStatement;
  }


}
