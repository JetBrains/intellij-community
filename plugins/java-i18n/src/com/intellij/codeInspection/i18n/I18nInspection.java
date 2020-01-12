// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.externalAnnotation.NonNlsAnnotationProvider;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.MethodUtils;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.uast.*;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public class I18nInspection extends AbstractBaseUastLocalInspectionTool implements CustomSuppressableInspectionTool {
  public boolean ignoreForAssertStatements = true;
  public boolean ignoreForExceptionConstructors = true;
  @NonNls
  public String ignoreForSpecifiedExceptionConstructors = "";
  public boolean ignoreForJUnitAsserts = true;
  public boolean ignoreForClassReferences = true;
  public boolean ignoreForPropertyKeyReferences = true;
  public boolean ignoreForNonAlpha = true;
  private boolean ignoreForAllButNls = false;
  public boolean ignoreAssignedToConstants;
  public boolean ignoreToString;
  @NonNls public String nonNlsCommentPattern = "NON-NLS";
  private boolean ignoreForEnumConstants;

  @Nullable private Pattern myCachedNonNlsPattern;
  @NonNls private static final String TO_STRING = "toString";

  public I18nInspection() {
    cacheNonNlsCommentPattern();
  }

  @Override
  public SuppressIntentionAction @NotNull [] getSuppressActions(PsiElement element) {
    SuppressQuickFix[] suppressActions = getBatchSuppressActions(element);

    if (myCachedNonNlsPattern == null) {
      return ContainerUtil.map2Array(suppressActions,  SuppressIntentionAction.class, SuppressIntentionActionFromFix::convertBatchToSuppressIntentionAction);
    }
    else {
      List<SuppressIntentionAction> suppressors = new ArrayList<>(suppressActions.length + 1);
      suppressors.add(new SuppressByCommentOutAction(nonNlsCommentPattern));
      suppressors.addAll(ContainerUtil.map(suppressActions,  SuppressIntentionActionFromFix::convertBatchToSuppressIntentionAction));
      return suppressors.toArray(SuppressIntentionAction.EMPTY_ARRAY);
    }
  }

  private static final String SKIP_FOR_ENUM = "ignoreForEnumConstant";
  private static final String IGNORE_ALL_BUT_NLS = "ignoreAllButNls";
  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (ignoreForEnumConstants) {
      node.addContent(new Element("option")
                        .setAttribute("name", SKIP_FOR_ENUM)
                        .setAttribute("value", Boolean.toString(ignoreForEnumConstants)));
    }
    if (ignoreForAllButNls) {
      node.addContent(new Element("option")
                        .setAttribute("name", IGNORE_ALL_BUT_NLS)
                        .setAttribute("value", Boolean.toString(ignoreForAllButNls)));
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element o : node.getChildren()) {
      String nameAttr = o.getAttributeValue("name");
      String valueAttr = o.getAttributeValue("value");
      if (Comparing.strEqual(nameAttr, SKIP_FOR_ENUM)) {
        if (valueAttr != null) {
          ignoreForEnumConstants = Boolean.parseBoolean(valueAttr);
        }
      }
      else if (Comparing.strEqual(nameAttr, IGNORE_ALL_BUT_NLS)) {
        if (valueAttr != null) {
          ignoreForAllButNls = Boolean.parseBoolean(valueAttr);
        }
      }
    }
    cacheNonNlsCommentPattern();
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "HardCodedStringLiteral";
  }

  @TestOnly
  public boolean setIgnoreForEnumConstants(boolean ignoreForEnumConstants) {
    boolean old = this.ignoreForEnumConstants;
    this.ignoreForEnumConstants = ignoreForEnumConstants;
    return old;
  }

  @TestOnly
  public boolean setIgnoreForAllButNls(boolean ignoreForAllButNls) {
    boolean old = this.ignoreForAllButNls;
    this.ignoreForAllButNls = ignoreForAllButNls;
    return old;
  }

  @Override
  public JComponent createOptionsPanel() {
    final GridBagLayout layout = new GridBagLayout();
    final JPanel panel = new JPanel(layout);
    final JCheckBox assertStatementsCheckbox = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.assert"), ignoreForAssertStatements);
    assertStatementsCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForAssertStatements = assertStatementsCheckbox.isSelected();
      }
    });
    final JCheckBox exceptionConstructorCheck =
      new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.for.exception.constructor.arguments"),
                    ignoreForExceptionConstructors);
    exceptionConstructorCheck.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForExceptionConstructors = exceptionConstructorCheck.isSelected();
      }
    });

    final JTextField specifiedExceptions = new JTextField(ignoreForSpecifiedExceptionConstructors);
    specifiedExceptions.getDocument().addDocumentListener(new DocumentAdapter(){
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        ignoreForSpecifiedExceptionConstructors = specifiedExceptions.getText();
      }
    });

    final JCheckBox junitAssertCheckbox = new JCheckBox(
      CodeInsightBundle.message("inspection.i18n.option.ignore.for.junit.assert.arguments"), ignoreForJUnitAsserts);
    junitAssertCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForJUnitAsserts = junitAssertCheckbox.isSelected();
      }
    });
    final JCheckBox classRef = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.qualified.class.names"), ignoreForClassReferences);
    classRef.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForClassReferences = classRef.isSelected();
      }
    });
    final JCheckBox propertyRef = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.property.keys"), ignoreForPropertyKeyReferences);
    propertyRef.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForPropertyKeyReferences = propertyRef.isSelected();
      }
    });
    final JCheckBox nonAlpha = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.nonalphanumerics"), ignoreForNonAlpha);
    nonAlpha.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForNonAlpha = nonAlpha.isSelected();
      }
    });
    final JCheckBox assignedToConstants = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.assigned.to.constants"), ignoreAssignedToConstants);
    assignedToConstants.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreAssignedToConstants = assignedToConstants.isSelected();
      }
    });
    final JCheckBox chkToString = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.tostring"), ignoreToString);
    chkToString.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreToString = chkToString.isSelected();
      }
    });

    final JCheckBox ignoreEnumConstants = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.enum"), ignoreForEnumConstants);
    ignoreEnumConstants.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForEnumConstants = ignoreEnumConstants.isSelected();
      }
    });

    final JCheckBox ignoreAllButNls = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.nls"), ignoreForAllButNls);
    ignoreAllButNls.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForAllButNls = ignoreAllButNls.isSelected();
      }
    });

    final GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.insets.bottom = 2;

    gc.gridx = GridBagConstraints.REMAINDER;
    gc.gridy = 0;
    gc.weightx = 1;
    gc.weighty = 0;
    panel.add(ignoreAllButNls, gc);

    gc.gridy ++;
    panel.add(assertStatementsCheckbox, gc);

    gc.gridy ++;
    panel.add(junitAssertCheckbox, gc);

    gc.gridy ++;
    panel.add(exceptionConstructorCheck, gc);

    gc.gridy ++;
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    panel.add(new FieldPanel(specifiedExceptions,
                             null,
                             CodeInsightBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"),
                             openProjects.length == 0 ? null :
                             new ActionListener() {
                               @Override
                               public void actionPerformed(@NotNull ActionEvent e) {
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
    panel.add(ignoreEnumConstants, gc);

    gc.gridy ++;
    gc.anchor = GridBagConstraints.NORTHWEST;
    gc.weighty = 1;
    final JTextField text = new JTextField(nonNlsCommentPattern);
    final FieldPanel nonNlsCommentPatternComponent =
      new FieldPanel(text, CodeInsightBundle.message("inspection.i18n.option.ignore.comment.pattern"),
                     CodeInsightBundle.message("inspection.i18n.option.ignore.comment.title"), null, () -> {
                       nonNlsCommentPattern = text.getText();
                       cacheNonNlsCommentPattern();
                     });
    panel.add(nonNlsCommentPatternComponent, gc);

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(null);
    scrollPane.setPreferredSize(new Dimension(panel.getPreferredSize().width + scrollPane.getVerticalScrollBar().getPreferredSize().width,
                                              panel.getPreferredSize().height +
                                              scrollPane.getHorizontalScrollBar().getPreferredSize().height));
    return scrollPane;
  }

  private DialogWrapper createIgnoreExceptionsConfigurationDialog(final Project project, final JTextField specifiedExceptions) {
    return new DialogWrapper(true) {
      private AddDeleteListPanel myPanel;
      {
        setTitle(CodeInsightBundle.message(
          "inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"));
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        final String[] ignored = ignoreForSpecifiedExceptionConstructors.split(",");
        final List<String> initialList = new ArrayList<>();
        for (String e : ignored) {
          if (!e.isEmpty()) initialList.add(e);
        }
        myPanel = new AddDeleteListPanel<String>(null, initialList) {
          @Override
          protected String findItemToAdd() {
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).
              createInheritanceClassChooser(
                CodeInsightBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"), scope,
                JavaPsiFacade.getInstance(project).findClass("java.lang.Throwable", scope), true, true, null);
            chooser.showDialog();
            PsiClass selectedClass = chooser.getSelected();
            return selectedClass != null ? selectedClass.getQualifiedName() : null;
          }
        };
        return myPanel;
      }

      @Override
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
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull UMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(method)) {
      return null;
    }
    List<ProblemDescriptor> results = new ArrayList<>();
    final UExpression body = method.getUastBody();
    if (body != null) {
      ProblemDescriptor[] descriptors = checkElement(body, manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(results, descriptors);
      }
    }
    checkAnnotations(method, manager, isOnTheFly, results);
    for (UParameter parameter : method.getUastParameters()) {
      checkAnnotations(parameter, manager, isOnTheFly, results);
    }
    return results.isEmpty() ? null : results.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(aClass)) {
      return null;
    }
    final UClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new ArrayList<>();
    for (UClassInitializer initializer : initializers) {
      final ProblemDescriptor[] descriptors = checkElement(initializer.getUastBody(), manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(result, descriptors);
      }
    }
    checkAnnotations(aClass, manager, isOnTheFly, result);


    return result.isEmpty() ? null : result.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private void checkAnnotations(UDeclaration member,
                                @NotNull InspectionManager manager,
                                boolean isOnTheFly, List<? super ProblemDescriptor> result) {
    for (UAnnotation annotation : member.getUAnnotations()) {
      final ProblemDescriptor[] descriptors = checkElement(annotation, manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(result, descriptors);
      }
    }
  }

  @Override
  public ProblemDescriptor @Nullable [] checkField(@NotNull UField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(field)) {
      return null;
    }
    if (AnnotationUtil.isAnnotated((PsiModifierListOwner)field.getJavaPsi(), AnnotationUtil.NON_NLS, CHECK_EXTERNAL)) {
      return null;
    }
    List<ProblemDescriptor> result = new ArrayList<>();
    final UExpression initializer = field.getUastInitializer();
    if (initializer != null) {
      ProblemDescriptor[] descriptors = checkElement(initializer, manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(result, descriptors);
      }
    } else if (field instanceof UEnumConstant) {
      List<UExpression> arguments = ((UEnumConstant)field).getValueArguments();
      for (UExpression argument : arguments) {
        ProblemDescriptor[] descriptors = checkElement(argument, manager, isOnTheFly);
        if (descriptors != null) {
          ContainerUtil.addAll(result, descriptors);
        }
      }
    }
    checkAnnotations(field, manager, isOnTheFly, result);
    return result.isEmpty() ? null : result.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "nls";
  }

  private ProblemDescriptor[] checkElement(@NotNull UElement element, @NotNull InspectionManager manager, boolean isOnTheFly) {
    StringI18nVisitor visitor = new StringI18nVisitor(manager, isOnTheFly);
    element.accept(visitor);
    List<ProblemDescriptor> problems = visitor.getProblems();
    return problems.isEmpty() ? null : problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @NotNull
  private static LocalQuickFix createIntroduceConstantFix() {
    return new LocalQuickFix() {
      @Override
      @NotNull
      public String getFamilyName() {
        return IntroduceConstantHandler.getRefactoringNameText();
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }

      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiExpression)) return;

        PsiExpression[] expressions = {(PsiExpression)element};
        new IntroduceConstantHandler().invoke(project, expressions);
      }
    };
  }

  private class StringI18nVisitor extends AbstractUastVisitor {
    private final List<ProblemDescriptor> myProblems = new ArrayList<>();
    private final InspectionManager myManager;
    private final boolean myOnTheFly;

    private StringI18nVisitor(@NotNull InspectionManager manager, boolean onTheFly) {
      myManager = manager;
      myOnTheFly = onTheFly;
    }

    @Override
    public boolean visitObjectLiteralExpression(@NotNull UObjectLiteralExpression objectLiteralExpression) {
      for (UExpression argument : objectLiteralExpression.getValueArguments()) {
        argument.accept(this);
      }

      return true;
    }
    
    @Override
    public boolean visitClass(@NotNull UClass node) {
      return false;
    }

    @Override
    public boolean visitField(@NotNull UField node) {
      return false;
    }

    @Override
    public boolean visitMethod(@NotNull UMethod node) {
      return false;
    }

    @Override
    public boolean visitInitializer(@NotNull UClassInitializer node) {
      return false;
    }

    @Override
    public boolean visitLiteralExpression(@NotNull ULiteralExpression expression) {
      Object value = expression.getValue();
      if (!(value instanceof String)) return false;
      String stringValue = (String)value;
      if (stringValue.trim().isEmpty()) {
        return false;
      }

      Set<PsiModifierListOwner> nonNlsTargets = new THashSet<>();
      if (canBeI18ned(myManager.getProject(), expression, stringValue, nonNlsTargets)) {
        UField parentField = UastUtils.getParentOfType(expression, UField.class); // PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (parentField != null) {
          nonNlsTargets.add(parentField);
        }

        final String description = CodeInsightBundle.message("inspection.i18n.message.general.with.value", "#ref");

        PsiElement sourcePsi = expression.getSourcePsi();
        
        List<LocalQuickFix> fixes = new ArrayList<>();

        if (sourcePsi instanceof PsiLiteralExpression) {
          if (myOnTheFly) {
            if (I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(sourcePsi) != null) {
              fixes.add(new I18nizeConcatenationQuickFix());
            }
            fixes.add(new I18nizeQuickFix());

            if (!isNotConstantFieldInitializer((PsiExpression)sourcePsi)) {
              fixes.add(createIntroduceConstantFix());
            }

            if (PsiUtil.isLanguageLevel5OrHigher(sourcePsi)) {
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(myManager.getProject());
              for (PsiModifierListOwner element : nonNlsTargets) {
                if (!AnnotationUtil.isAnnotated(element, AnnotationUtil.NLS, CHECK_HIERARCHY | CHECK_EXTERNAL)) {
                  if (!element.getManager().isInProject(element) ||
                      facade.findClass(AnnotationUtil.NON_NLS, element.getResolveScope()) != null) {
                    fixes.add(new NonNlsAnnotationProvider().createFix(element));
                  }
                }
              }
            }
          }
          else if (Registry.is("i18n.for.idea.project") &&
                   I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(sourcePsi) == null) {
            fixes.add(new I18nizeBatchQuickFix());
          }
        }

        LocalQuickFix[] farr = fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
        final ProblemDescriptor problem = myManager.createProblemDescriptor(sourcePsi,
                                                                            description, myOnTheFly, farr,
                                                                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        myProblems.add(problem);
      }
      return false;
    }

    private boolean isNotConstantFieldInitializer(final PsiExpression expression) {
      PsiField parentField = expression.getParent() instanceof PsiField ? (PsiField)expression.getParent() : null;
      return parentField != null && expression == parentField.getInitializer() &&
             parentField.hasModifierProperty(PsiModifier.FINAL) &&
             parentField.hasModifierProperty(PsiModifier.STATIC);
    }

    @Override
    public boolean visitAnnotation(UAnnotation annotation) {
      //prevent from @SuppressWarnings
      if (BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME.equals(annotation.getQualifiedName())) {
        return true;
      }
      return super.visitAnnotation(annotation);
    }

    private List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }

  private boolean canBeI18ned(@NotNull Project project,
                              @NotNull ULiteralExpression expression,
                              @NotNull String value,
                              @NotNull Set<? super PsiModifierListOwner> nonNlsTargets) {
    if (ignoreForNonAlpha && !StringUtil.containsAlphaCharacters(value)) {
      return false;
    }

    if (ignoreForAllButNls) {
      return JavaI18nUtil.isPassedToAnnotatedParam(expression, AnnotationUtil.NLS, null);
    }

    if (JavaI18nUtil.isPassedToAnnotatedParam(expression, AnnotationUtil.NON_NLS, nonNlsTargets)) {
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

    if (JavaI18nUtil.mustBePropertyKey(expression, null)) {
      return false;
    }

    if (isReturnedFromNonNlsMethod(expression, nonNlsTargets)) {
      return false;
    }
    if (ignoreForAssertStatements && isArgOfAssertStatement(expression)) {
      return false;
    }
    if (ignoreForExceptionConstructors && isExceptionArgument(expression)) {
      return false;
    }
    if (ignoreForEnumConstants && isArgOfEnumConstant(expression)) {
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
    if (ignoreForPropertyKeyReferences && !PropertiesImplUtil.findPropertiesByKey(project, value).isEmpty()) {
      return false;
    }
    if (ignoreToString && isToString(expression)) {
      return false;
    }

    Pattern pattern = myCachedNonNlsPattern;
    if (pattern != null) {
      PsiFile file = expression.getSourcePsi().getContainingFile();
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      int line = document.getLineNumber(expression.getSourcePsi().getTextRange().getStartOffset());
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

  private static boolean isArgOfEnumConstant(ULiteralExpression expression) {
    return expression.getUastParent() instanceof UEnumConstant;
  }

  public void cacheNonNlsCommentPattern() {
    myCachedNonNlsPattern = nonNlsCommentPattern.trim().isEmpty() ? null : Pattern.compile(nonNlsCommentPattern);
  }

  private static boolean isClassRef(final ULiteralExpression expression, String value) {
    if (StringUtil.startsWithChar(value,'#')) {
      value = value.substring(1); // A favor for JetBrains team to catch common Logger usage practice.
    }

    Project project = Objects.requireNonNull(expression.getSourcePsi()).getProject();
    return JavaPsiFacade.getInstance(project).findClass(value, GlobalSearchScope.allScope(project)) != null;
  }

  private static boolean isClassNonNls(@NotNull UDeclaration clazz) {
    UFile uFile = UastUtils.getContainingUFile(clazz);
    if (uFile == null) return false;
    final PsiDirectory directory = uFile.getSourcePsi().getContainingDirectory();
    return directory != null && isPackageNonNls(JavaDirectoryService.getInstance().getPackage(directory));
  }

  public static boolean isPackageNonNls(final PsiPackage psiPackage) {
    if (psiPackage == null || psiPackage.getName() == null) {
      return false;
    }
    final PsiModifierList pkgModifierList = psiPackage.getAnnotationList();
    return pkgModifierList != null && pkgModifierList.hasAnnotation(AnnotationUtil.NON_NLS)
           || isPackageNonNls(psiPackage.getParentPackage());
  }

  private boolean isPassedToNonNlsVariable(@NotNull ULiteralExpression expression,
                                           final Set<? super PsiModifierListOwner> nonNlsTargets) {
    UExpression toplevel = JavaI18nUtil.getTopLevelExpression(expression);
    PsiModifierListOwner var = null;
    if (UastExpressionUtils.isAssignment(toplevel)) {
      UExpression lExpression = ((UBinaryExpression)toplevel).getLeftOperand();
      while (lExpression instanceof UArrayAccessExpression) {
        lExpression = ((UArrayAccessExpression)lExpression).getReceiver();
      }
      if (lExpression instanceof UResolvable) {
        final PsiElement resolved = ((UResolvable)lExpression).resolve();
        if (resolved instanceof PsiVariable) var = (PsiVariable)resolved;
      }
    }

    if (var == null) {
      UElement parent = toplevel.getUastParent();
      if (parent instanceof UVariable && toplevel.equals(((UVariable)parent).getUastInitializer())) {
        if (((UVariable)parent).findAnnotation(AnnotationUtil.NON_NLS) != null) {
          return true;
        }

        PsiElement psi = parent.getSourcePsi();
        if (psi instanceof PsiModifierListOwner) {
          var = (PsiModifierListOwner)psi;
        }
      }
      else if (toplevel instanceof USwitchExpression) {
        UExpression switchExpression = ((USwitchExpression)toplevel).getExpression();
        if (switchExpression instanceof UResolvable) {
          PsiElement resolved = ((UResolvable)switchExpression).resolve();
          if (resolved instanceof PsiVariable) {
            UElement caseParent = expression.getUastParent();
            if (caseParent instanceof USwitchClauseExpression && ((USwitchClauseExpression)caseParent).getCaseValues().contains(expression)) {
              var = (PsiVariable)resolved;
            }
          }
        }
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
        return JavaI18nUtil.isMethodParameterAnnotatedWith(method, index, null, AnnotationUtil.NON_NLS, null, null);
      }
    }
    return AnnotationUtil.isAnnotated(parent, AnnotationUtil.NON_NLS, CHECK_EXTERNAL);
  }

  private static boolean isInNonNlsEquals(ULiteralExpression expression, final Set<? super PsiModifierListOwner> nonNlsTargets) {
    UElement parent = UastUtils.skipParenthesizedExprUp(expression.getUastParent());
    if (!(parent instanceof UQualifiedReferenceExpression)) return false;
    UExpression selector = ((UQualifiedReferenceExpression)parent).getSelector();
    if (!(selector instanceof UCallExpression)) return false;
    UCallExpression call = (UCallExpression)selector;
    if (!HardcodedMethodConstants.EQUALS.equals(call.getMethodName()) ||
        !MethodUtils.isEquals(call.resolve())) return false;
    final List<UExpression> expressions = call.getValueArguments();
    if (expressions.size() != 1) return false;
    final UExpression arg = UastUtils.skipParenthesizedExprDown(expressions.get(0));
    UResolvable ref = ObjectUtils.tryCast(arg, UResolvable.class);
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

  private static boolean isInNonNlsCall(@NotNull UExpression expression,
                                        final Set<? super PsiModifierListOwner> nonNlsTargets) {
    UExpression parent = UastUtils.skipParenthesizedExprDown(JavaI18nUtil.getTopLevelExpression(expression));
    if (parent instanceof UQualifiedReferenceExpression) {
      return isNonNlsCall((UQualifiedReferenceExpression)parent, nonNlsTargets);
    }
    else if (parent != null && UastExpressionUtils.isAssignment(parent)) {
      UExpression operand = ((UBinaryExpression)parent).getLeftOperand();
      if (operand instanceof UReferenceExpression &&
          isNonNlsCall((UReferenceExpression)operand, nonNlsTargets)) return true;
    }
    else if (parent instanceof UCallExpression) {
      UElement parentOfNew = UastUtils.skipParenthesizedExprUp(parent.getUastParent());
      if (parentOfNew instanceof ULocalVariable) {
        final ULocalVariable newVariable = (ULocalVariable)parentOfNew;
        if (annotatedAsNonNls(newVariable.getPsi())) {
          return true;
        }
        nonNlsTargets.add(newVariable);
        return false;
      }
    }

    return false;
  }

  private static boolean isNonNlsCall(UReferenceExpression qualifier, Set<? super PsiModifierListOwner> nonNlsTargets) {
    final PsiElement resolved = qualifier.resolve();
    if (resolved instanceof PsiModifierListOwner) {
      final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)resolved;
      if (annotatedAsNonNls(modifierListOwner)) {
        return true;
      }
      nonNlsTargets.add(modifierListOwner);
    }
    if (qualifier instanceof UQualifiedReferenceExpression) {
      UExpression receiver = UastUtils.skipParenthesizedExprDown(((UQualifiedReferenceExpression)qualifier).getReceiver());
      if (receiver instanceof UReferenceExpression) {
        return isNonNlsCall((UReferenceExpression)receiver, nonNlsTargets);
      }
    }
    return false;
  }

  private static boolean isReturnedFromNonNlsMethod(final ULiteralExpression expression, final Set<? super PsiModifierListOwner> nonNlsTargets) {
    PsiMethod method;
    UNamedExpression nameValuePair = UastUtils.getParentOfType(expression, UNamedExpression.class);
    if (nameValuePair != null) {
      method = UastUtils.getAnnotationMethod(nameValuePair);
    }
    else {
      //todo return from lambda
      UElement parent = expression.getUastParent();
      while (parent instanceof UCallExpression && 
             ((UCallExpression)parent).getKind() == UastCallKind.NEW_ARRAY_WITH_INITIALIZER) {
        parent = parent.getUastParent();
      }
      final UElement returnStmt = UastUtils.getParentOfType(parent, UReturnExpression.class, false, UCallExpression.class, ULambdaExpression.class);
      if (!(returnStmt instanceof UReturnExpression)) {
        return false;
      }
      UMethod uMethod = UastUtils.getParentOfType(expression, UMethod.class);
      method = uMethod != null ? uMethod.getJavaPsi() : null;
    }
    if (method == null) return false;

    if (AnnotationUtil.isAnnotated(method, AnnotationUtil.NON_NLS, CHECK_HIERARCHY | CHECK_EXTERNAL)) {
      return true;
    }
    nonNlsTargets.add(method);
    return false;
  }

  private static boolean isToString(final ULiteralExpression expression) {
    final UMethod method = UastUtils.getParentOfType(expression, UMethod.class);
    if (method == null) return false;
    final PsiType returnType = method.getReturnType();
    return TO_STRING.equals(method.getName())
           && method.getUastParameters().isEmpty()
           && returnType != null
           && "java.lang.String".equals(returnType.getCanonicalText());
  }

  private static boolean isArgOfJUnitAssertion(ULiteralExpression expression) {
    final UElement parent = UastUtils.skipParenthesizedExprUp(expression.getUastParent());
    if (parent == null || !UastExpressionUtils.isMethodCall(parent)) {
      return false;
    }
    @NonNls final String methodName = ((UCallExpression)parent).getMethodName();
    if (methodName == null) {
      return false;
    }

    if (!methodName.startsWith("assert") && !methodName.equals("fail")) {
      return false;
    }
    final PsiMethod method = ((UCallExpression)parent).resolve();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    return InheritanceUtil.isInheritor(containingClass,"org.junit.Assert") ||
           InheritanceUtil.isInheritor(containingClass,"org.junit.jupiter.api.Assertions") || 
           InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert");
  }

  private static boolean isArgOfSpecifiedExceptionConstructor(ULiteralExpression expression, 
                                                              String[] specifiedExceptions) {
    if (specifiedExceptions.length == 0) return false;

    UCallExpression parent = UastUtils.getParentOfType(expression, UCallExpression.class, true, UClass.class);
    if (parent == null || !UastExpressionUtils.isConstructorCall(parent)) {
      return false;
    }
    final PsiMethod resolved = parent.resolve();
    final PsiClass aClass = resolved != null ? resolved.getContainingClass() : null;
    if (aClass == null) {
      return false;
    }

    return ArrayUtil.contains(aClass.getQualifiedName(), specifiedExceptions);
  }

  private static boolean isArgOfAssertStatement(UExpression expression) {
    UCallExpression parent = UastUtils.getParentOfType(expression, UCallExpression.class);
    return parent != null && "assert".equals(parent.getMethodName());
  }
  
   public static boolean isExceptionArgument(@NotNull UExpression expression) {
    final UCallExpression newExpression =
      UastUtils.getParentOfType(expression, UCallExpression.class, true, UBlockExpression.class, UClass.class);
    if (newExpression != null) {
      if (UastExpressionUtils.isConstructorCall(newExpression)) {
        final PsiType newExpressionType = newExpression.getExpressionType();
        return InheritanceUtil.isInheritor(newExpressionType, CommonClassNames.JAVA_LANG_THROWABLE);
      }
      else if (UastExpressionUtils.isMethodCall(newExpression)) {
        String methodName = newExpression.getMethodName();
        if (PsiKeyword.SUPER.equals(methodName) || PsiKeyword.THIS.equals(methodName)) {
          PsiMethod ctor = newExpression.resolve();
          return ctor != null &&
                 InheritanceUtil.isInheritor(ctor.getContainingClass(), CommonClassNames.JAVA_LANG_THROWABLE);
        }
      }
    }
    return false;
  }

}
