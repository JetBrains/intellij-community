// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.externalAnnotation.NonNlsAnnotationProvider;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.RegExp;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.jetbrains.uast.util.UastExpressionUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

public class I18nInspection extends AbstractBaseUastLocalInspectionTool implements CustomSuppressableInspectionTool {
  private static final CallMatcher IGNORED_METHODS = CallMatcher.anyOf( 
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "substring", "trim"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf").parameterTypes("int"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf").parameterTypes("double"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf").parameterTypes("long"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf").parameterTypes("char"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "toString"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "toString"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "toString"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_IO_FILE, "getAbsolutePath", "getCanonicalPath", "getName", "getPath"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_THROWABLE, "getMessage").parameterCount(0),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_THROWABLE, "toString").parameterCount(0)
  );
  @RegExp private static final String DEFAULT_NON_NLS_LITERAL_PATTERN = "(?i)https?://.+|\\w*[.][\\w.]+|\\w*[$]\\w*|(</?(html|b|i|body|li|ol|ul)>)*|&\\w+;";
  private static final CallMatcher STRING_LENGTH =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "length").parameterCount(0);
  private static final CallMatcher STRING_EQUALS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "equals", "equalsIgnoreCase").parameterCount(1);

  public boolean ignoreForAssertStatements = true;
  public boolean ignoreForExceptionConstructors = true;
  public @NlsSafe String ignoreForSpecifiedExceptionConstructors = "";
  public boolean ignoreForJUnitAsserts = true;
  public boolean ignoreForClassReferences = true;
  public boolean ignoreForPropertyKeyReferences = true;
  public boolean ignoreForNonAlpha = true;
  private boolean ignoreForAllButNls = false;
  private boolean reportUnannotatedReferences = false;
  public boolean ignoreAssignedToConstants;
  public boolean ignoreToString;
  @NlsSafe private String nonNlsLiteralPattern;
  @NlsSafe public String nonNlsCommentPattern;
  private boolean ignoreForEnumConstants;

  @Nullable private Pattern myCachedCommentPattern;
  @Nullable private Pattern myCachedLiteralPattern;
  @NonNls private static final String TO_STRING = "toString";

  public I18nInspection() {
    setNonNlsCommentPattern("NON-NLS");
    setNonNlsLiteralPattern(DEFAULT_NON_NLS_LITERAL_PATTERN);
  }

  @Override
  public SuppressIntentionAction @NotNull [] getSuppressActions(PsiElement element) {
    SuppressQuickFix[] suppressActions = getBatchSuppressActions(element);

    if (myCachedCommentPattern == null) {
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
  private static final String REPORT_UNANNOTATED_REFERENCES = "reportUnannotatedReferences";
  private static final String NON_NLS_LITERAL_PATTERN = "nonNlsLiteralPattern";
  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (ignoreForEnumConstants) {
      node.addContent(new Element("option")
                        .setAttribute("name", SKIP_FOR_ENUM)
                        .setAttribute("value", Boolean.toString(ignoreForEnumConstants)));
    }
    if (reportUnannotatedReferences) {
      node.addContent(new Element("option")
                        .setAttribute("name", REPORT_UNANNOTATED_REFERENCES)
                        .setAttribute("value", Boolean.toString(reportUnannotatedReferences)));
    }
    if (ignoreForAllButNls) {
      node.addContent(new Element("option")
                        .setAttribute("name", IGNORE_ALL_BUT_NLS)
                        .setAttribute("value", Boolean.toString(ignoreForAllButNls)));
    }
    if (!nonNlsLiteralPattern.equals(DEFAULT_NON_NLS_LITERAL_PATTERN)) {
      node.addContent(new Element("option")
                        .setAttribute("name", NON_NLS_LITERAL_PATTERN)
                        .setAttribute("value", DEFAULT_NON_NLS_LITERAL_PATTERN));
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
      else if (Comparing.strEqual(nameAttr, REPORT_UNANNOTATED_REFERENCES)) {
        if (valueAttr != null) {
          reportUnannotatedReferences = Boolean.parseBoolean(valueAttr);
        }
      }
      else if (Comparing.strEqual(nameAttr, NON_NLS_LITERAL_PATTERN)) {
        if (valueAttr != null) {
          setNonNlsLiteralPattern(valueAttr);
        }
      }
    }
    setNonNlsCommentPattern(nonNlsCommentPattern);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.internationalization.issues");
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
  public boolean setReportUnannotatedReferences(boolean reportUnannotatedReferences) {
    boolean old = this.reportUnannotatedReferences;
    this.reportUnannotatedReferences = reportUnannotatedReferences;
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
    final JCheckBox assertStatementsCheckbox = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.assert"), ignoreForAssertStatements);
    assertStatementsCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForAssertStatements = assertStatementsCheckbox.isSelected();
      }
    });
    final JCheckBox exceptionConstructorCheck =
      new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.for.exception.constructor.arguments"),
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
      JavaI18nBundle.message("inspection.i18n.option.ignore.for.junit.assert.arguments"), ignoreForJUnitAsserts);
    junitAssertCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForJUnitAsserts = junitAssertCheckbox.isSelected();
      }
    });
    final JCheckBox classRef = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.qualified.class.names"), ignoreForClassReferences);
    classRef.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForClassReferences = classRef.isSelected();
      }
    });
    final JCheckBox propertyRef = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.property.keys"), ignoreForPropertyKeyReferences);
    propertyRef.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForPropertyKeyReferences = propertyRef.isSelected();
      }
    });
    final JCheckBox nonAlpha = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.nonalphanumerics"), ignoreForNonAlpha);
    nonAlpha.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForNonAlpha = nonAlpha.isSelected();
      }
    });
    final JCheckBox assignedToConstants = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.assigned.to.constants"), ignoreAssignedToConstants);
    assignedToConstants.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreAssignedToConstants = assignedToConstants.isSelected();
      }
    });
    final JCheckBox chkToString = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.tostring"), ignoreToString);
    chkToString.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreToString = chkToString.isSelected();
      }
    });

    final JCheckBox ignoreEnumConstants = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.enum"), ignoreForEnumConstants);
    ignoreEnumConstants.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForEnumConstants = ignoreEnumConstants.isSelected();
      }
    });

    final JCheckBox reportRefs = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.report.unannotated.refs"), reportUnannotatedReferences);
    reportRefs.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        reportUnannotatedReferences = reportRefs.isSelected();
      }
    });
    reportRefs.setEnabled(ignoreForAllButNls);
    final JCheckBox ignoreAllButNls = new JCheckBox(JavaI18nBundle.message("inspection.i18n.option.ignore.nls"), ignoreForAllButNls);
    ignoreAllButNls.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        ignoreForAllButNls = ignoreAllButNls.isSelected();
        reportRefs.setEnabled(ignoreForAllButNls);
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
    panel.add(reportRefs, gc);

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
                             JavaI18nBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"),
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
    final JTextField commentPattern = new JTextField(nonNlsCommentPattern);
    final FieldPanel nonNlsCommentPatternComponent =
      new FieldPanel(commentPattern, JavaI18nBundle.message("inspection.i18n.option.ignore.comment.pattern"),
                     JavaI18nBundle.message("inspection.i18n.option.ignore.comment.title"), null, 
                     () -> setNonNlsCommentPattern(commentPattern.getText()));
    panel.add(nonNlsCommentPatternComponent, gc);
    gc.gridy ++;
    gc.anchor = GridBagConstraints.NORTHWEST;
    gc.weighty = 1;
    final JTextField literalPattern = new ExpandableTextField(text -> Collections.singletonList(text),
                                                              strings -> StringUtil.join(strings, "|"));
    literalPattern.setText(nonNlsLiteralPattern);
    final FieldPanel nonNlsStringPatternComponent =
      new FieldPanel(literalPattern, JavaI18nBundle.message("inspection.i18n.option.ignore.string.pattern"),
                     JavaI18nBundle.message("inspection.i18n.option.ignore.string.title"), null, 
                     () -> setNonNlsLiteralPattern(literalPattern.getText()));
    panel.add(nonNlsStringPatternComponent, gc);

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
      private AddDeleteListPanel<?> myPanel;
      {
        setTitle(JavaI18nBundle.message(
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
                JavaI18nBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"), scope,
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
    checkMethodBody(method, manager, isOnTheFly, results);
    checkAnnotations(method, manager, isOnTheFly, results);
    for (UParameter parameter : method.getUastParameters()) {
      checkAnnotations(parameter, manager, isOnTheFly, results);
    }
    return results.isEmpty() ? null : results.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private void checkMethodBody(@NotNull UMethod method,
                               @NotNull InspectionManager manager,
                               boolean isOnTheFly,
                               @NotNull List<ProblemDescriptor> results) {
    final UExpression body = method.getUastBody();
    if (body != null) {
      if (body.getSourcePsi() != null) {
        addAll(results, checkElement(body, manager, isOnTheFly));
      }
      else if (body instanceof UBlockExpression) { // fake blocks are popular in Kotlin
        for (UExpression expression : ((UBlockExpression)body).getExpressions()) {
          // Kotlin expression body
          if ((expression instanceof UReturnExpression) && (expression.getSourcePsi() == null)) {
            UExpression returnExpression = ((UReturnExpression)expression).getReturnExpression();
            if (returnExpression != null) {
              addAll(results, checkElement(returnExpression, manager, isOnTheFly));
            }
          }
          // ordinary physical body
          else {
            addAll(results, checkElement(expression, manager, isOnTheFly));
          }
        }
      }
    }
  }

  private static void addAll(List<? super ProblemDescriptor> results, ProblemDescriptor[] descriptors) {
    if (descriptors != null) {
      ContainerUtil.addAll(results, descriptors);
    }
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(aClass)) {
      return null;
    }
    final UClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new ArrayList<>();

    for (UMethod method : aClass.getMethods()) {
      if (method.getSourcePsi() == aClass.getSourcePsi()) { // primary constructor that will not be processed other way
        checkMethodBody(method, manager, isOnTheFly, result);
      }
    }

    for (UClassInitializer initializer : initializers) {
      addAll(result, checkElement(initializer.getUastBody(), manager, isOnTheFly));
    }
    checkAnnotations(aClass, manager, isOnTheFly, result);


    return result.isEmpty() ? null : result.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private void checkAnnotations(UDeclaration member,
                                @NotNull InspectionManager manager,
                                boolean isOnTheFly, List<? super ProblemDescriptor> result) {
    for (UAnnotation annotation : member.getUAnnotations()) {
      addAll(result, checkElement(annotation, manager, isOnTheFly));
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
      addAll(result, checkElement(initializer, manager, isOnTheFly));
    } else if (field instanceof UEnumConstant) {
      List<UExpression> arguments = ((UEnumConstant)field).getValueArguments();
      for (UExpression argument : arguments) {
        addAll(result, checkElement(argument, manager, isOnTheFly));
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
    if (element instanceof ULiteralExpression) {
      element = UastLiteralUtils.wrapULiteral((ULiteralExpression)element);
    }

    PsiElement sourcePsi = element.getSourcePsi();
    if (sourcePsi == null) return ProblemDescriptor.EMPTY_ARRAY;
    StringI18nVisitor visitor = new StringI18nVisitor(manager, isOnTheFly);
    sourcePsi.accept(visitor);
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

  private final class StringI18nVisitor extends PsiElementVisitor {
    private final List<ProblemDescriptor> myProblems = new ArrayList<>();
    private final InspectionManager myManager;
    private final boolean myOnTheFly;

    private StringI18nVisitor(@NotNull InspectionManager manager, boolean onTheFly) {
      myManager = manager;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      super.visitElement(element);

      if (element instanceof PsiMember && ((PsiMember)element).getName() != null ||
          element instanceof PsiClassInitializer) {
        return;
      }

      UElement uElement;
      if (ignoreForAllButNls && reportUnannotatedReferences) {
        uElement = UastContextKt.toUElementOfExpectedTypes(element, UInjectionHost.class, UAnnotation.class, UCallExpression.class,
                                                           UReferenceExpression.class);
      }
      else {
        uElement = UastContextKt.toUElementOfExpectedTypes(element, UInjectionHost.class, UAnnotation.class);
      }

      if (uElement instanceof UInjectionHost) {
        visitLiteralExpression(element, (UInjectionHost)uElement);
        return;
      }
      
      if (uElement instanceof UCallExpression) {
        visitCallExpression(element, (UCallExpression)uElement);
      }
      
      if (uElement instanceof UReferenceExpression) {
        visitReferenceExpression(element, (UReferenceExpression)uElement);
      }

      if (uElement instanceof UAnnotation) {
        //prevent from @SuppressWarnings
        if (BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME.equals(((UAnnotation)uElement).getQualifiedName())) {
          return;
        }
      }

      element.acceptChildren(this);
    }

    private void visitCallExpression(@NotNull PsiElement sourcePsi, @NotNull UCallExpression ref) {
      PsiMethod target = ref.resolve();
      if (target == null) return;
      if (IGNORED_METHODS.methodMatches(target)) return;
      UExpression expr = ref;
      if (ref.getUastParent() instanceof UQualifiedReferenceExpression) {
        expr = (UQualifiedReferenceExpression)ref.getUastParent();
      }
      processReferenceToNonLocalized(sourcePsi, expr, target);
    }

    private void visitReferenceExpression(@NotNull PsiElement sourcePsi, @NotNull UReferenceExpression ref) {
      PsiVariable target = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
      if (target == null || target instanceof PsiLocalVariable) return;
      processReferenceToNonLocalized(sourcePsi, ref, target);
    }

    private void processReferenceToNonLocalized(@NotNull PsiElement sourcePsi, @NotNull UExpression ref, PsiModifierListOwner target) {
      PsiType type = ref.getExpressionType();
      if (!TypeUtils.isJavaLangString(type)) return;
      if (NlsInfo.forModifierListOwner(target).canBeUsedInLocalizedContext()) return;
      if (NlsInfo.forType(type).canBeUsedInLocalizedContext()) return;
      
      String value = target instanceof PsiVariable ? ObjectUtils.tryCast(((PsiVariable)target).computeConstantValue(), String.class) : null;

      NlsInfo targetInfo = getExpectedNlsInfo(myManager.getProject(), ref, value, new THashSet<>());
      if (targetInfo instanceof NlsInfo.Localized) {
        AddAnnotationFix fix =
          new AddAnnotationFix(((NlsInfo.Localized)targetInfo).suggestAnnotation(target), target, AnnotationUtil.NON_NLS);
        AddAnnotationFix fixSafe = null;
        if (JavaPsiFacade.getInstance(target.getProject()).findClass(NlsInfo.NLS_SAFE, target.getResolveScope()) != null) {
          fixSafe = new MarkAsSafeFix(target);
        }
        String description = JavaI18nBundle.message("inspection.i18n.message.non.localized.passed.to.localized");
        final ProblemDescriptor problem = myManager.createProblemDescriptor(
          sourcePsi, description, myOnTheFly, new LocalQuickFix[]{fix, fixSafe}, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        myProblems.add(problem);
      }
    }

    private void visitLiteralExpression(@NotNull PsiElement sourcePsi, @NotNull UInjectionHost expression) {
      String stringValue = getStringValueOfKnownPart(expression);
      if (StringUtil.isEmptyOrSpaces(stringValue)) {
        return;
      }

      Set<PsiModifierListOwner> nonNlsTargets = new THashSet<>();
      NlsInfo info = getExpectedNlsInfo(myManager.getProject(), expression, stringValue, nonNlsTargets);
      if (!(info instanceof NlsInfo.Localized)) {
        return;
      }
      UField parentField =
        UastUtils.getParentOfType(expression, UField.class); // PsiTreeUtil.getParentOfType(expression, PsiField.class);
      if (parentField != null) {
        nonNlsTargets.add((PsiModifierListOwner)parentField.getJavaPsi());
      }

      final String description = JavaI18nBundle.message("inspection.i18n.message.general.with.value", "#ref");

      List<LocalQuickFix> fixes = new ArrayList<>();

      if (sourcePsi instanceof PsiLiteralExpression && PsiUtil.isLanguageLevel5OrHigher(sourcePsi)) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(myManager.getProject());
        for (PsiModifierListOwner element : nonNlsTargets) {
          if (NlsInfo.forModifierListOwner(element).getNlsStatus() == ThreeState.UNSURE) {
            if (!element.getManager().isInProject(element) ||
                facade.findClass(AnnotationUtil.NON_NLS, element.getResolveScope()) != null) {
              fixes.add(new NonNlsAnnotationProvider().createFix(element));
            }
          }
        }
      }

      if (myOnTheFly) {
        fixes.add(new I18nizeQuickFix((NlsInfo.Localized)info));
        if (I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(sourcePsi) != null) {
          fixes.add(new I18nizeConcatenationQuickFix((NlsInfo.Localized)info));
        }

        if (sourcePsi instanceof PsiLiteralExpression && !isNotConstantFieldInitializer((PsiExpression)sourcePsi)) {
          fixes.add(createIntroduceConstantFix());
        }
      }
      else {
        fixes.add(new I18nizeBatchQuickFix());
      }

      LocalQuickFix[] farr = fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
      final ProblemDescriptor problem = myManager.createProblemDescriptor(sourcePsi,
                                                                          description, myOnTheFly, farr,
                                                                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      myProblems.add(problem);
    }

    private boolean isNotConstantFieldInitializer(final PsiExpression expression) {
      PsiField parentField = expression.getParent() instanceof PsiField ? (PsiField)expression.getParent() : null;
      return parentField != null && expression == parentField.getInitializer() &&
             parentField.hasModifierProperty(PsiModifier.FINAL) &&
             parentField.hasModifierProperty(PsiModifier.STATIC);
    }

    private List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }

  private static String getStringValueOfKnownPart(@NotNull UInjectionHost expression) {
    UStringConcatenationsFacade concatenationsFacade = UStringConcatenationsFacade.createFromUExpression(expression);
    if (concatenationsFacade != null) {
      return concatenationsFacade.asPartiallyKnownString().getConcatenationOfKnown();
    }
    else {
      return expression.evaluateToString();
    }
  }

  private static List<UExpression> findIndirectUsages(UExpression expression) {
    PsiElement sourcePsi = expression.getSourcePsi();
    if (!(sourcePsi instanceof PsiLiteralExpression)) {
      return Collections.emptyList();
    }

    while (true) {
      PsiElement psiParent = sourcePsi.getParent();
      if (psiParent instanceof PsiPolyadicExpression || psiParent instanceof PsiParenthesizedExpression ||
          (psiParent instanceof PsiConditionalExpression && ((PsiConditionalExpression)psiParent).getCondition() != sourcePsi)) {
        sourcePsi = psiParent;
      }
      else {
        break;
      }
    }

    PsiExpression passThrough = ExpressionUtils.getPassThroughExpression((PsiExpression)sourcePsi);
    PsiElement parent = passThrough.getParent();
    List<UExpression> expressions = new ArrayList<>();
    if (!passThrough.equals(sourcePsi)) {
      expressions.add(UastContextKt.toUElement(passThrough, UExpression.class));
    }

    PsiLocalVariable local = null;
    if (parent instanceof PsiLocalVariable) {
      local = (PsiLocalVariable)parent;
    }
    else if (parent instanceof PsiAssignmentExpression &&
             passThrough.equals(((PsiAssignmentExpression)parent).getRExpression()) &&
             ((PsiAssignmentExpression)parent).getOperationTokenType() == JavaTokenType.EQ) {
      local = ExpressionUtils.resolveLocalVariable(((PsiAssignmentExpression)parent).getLExpression());
    }

    if (local != null && NlsInfo.forModifierListOwner(local).getNlsStatus() == ThreeState.UNSURE) {
      PsiElement codeBlock = PsiUtil.getVariableCodeBlock(local, null);
      if (codeBlock instanceof PsiCodeBlock) {
        for (PsiElement e : DefUseUtil.getRefs(((PsiCodeBlock)codeBlock), local, passThrough)) {
          ContainerUtil.addIfNotNull(expressions, UastContextKt.toUElement(e, UExpression.class));
        }
      }
    }
    return expressions;
  }

  private NlsInfo getExpectedNlsInfo(@NotNull Project project,
                                     @NotNull UExpression expression,
                                     @Nullable String value,
                                     @NotNull Set<? super PsiModifierListOwner> nonNlsTargets) {
    if (ignoreForNonAlpha && value != null && !StringUtil.containsAlphaCharacters(value)) {
      return NlsInfo.nonLocalized();
    }

    if (isSuppressedByComment(project, expression)) {
      return NlsInfo.nonLocalized();
    }

    if (value != null && myCachedLiteralPattern != null && myCachedLiteralPattern.matcher(value).matches()) {
      return NlsInfo.nonLocalized();
    }

    List<UExpression> usages = findIndirectUsages(expression);
    if (usages.isEmpty()) {
      usages = Collections.singletonList(expression);
    }

    for (UExpression usage : usages) {
      NlsInfo info = NlsInfo.forExpression(usage);
      switch (info.getNlsStatus()) {
        case YES: {
          return info;
        }
        case UNSURE: {
          if (ignoreForAllButNls) {
            break;
          }
          if (shouldIgnoreUsage(project, value, nonNlsTargets, usage)) {
            break;
          }
          ContainerUtil.addIfNotNull(nonNlsTargets, ((NlsInfo.Unspecified)info).getAnnotationCandidate());
          return NlsInfo.localized();
        }
        case NO:
          break;
      }
    }
    return NlsInfo.nonLocalized();
  }

  private boolean isSuppressedByComment(@NotNull Project project, @NotNull UExpression expression) {
    Pattern pattern = myCachedCommentPattern;
    if (pattern != null) {
      PsiElement sourcePsi = expression.getSourcePsi();
      if (sourcePsi == null) return false;
      PsiFile file = sourcePsi.getContainingFile();
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null) return false;
      int line = document.getLineNumber(sourcePsi.getTextRange().getStartOffset());
      int lineStartOffset = document.getLineStartOffset(line);
      CharSequence lineText = document.getCharsSequence().subSequence(lineStartOffset, document.getLineEndOffset(line));

      Matcher matcher = pattern.matcher(lineText);
      int start = 0;
      while (matcher.find(start)) {
        start = matcher.start();
        PsiElement element = file.findElementAt(lineStartOffset + start);
        if (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) return true;
        if (start == lineText.length() - 1) break;
        start++;
      }
    }
    return false;
  }

  private boolean shouldIgnoreUsage(@NotNull Project project,
                                    @Nullable String value,
                                    @NotNull Set<? super PsiModifierListOwner> nonNlsTargets,
                                    @NotNull UExpression usage) {
    if (isInNonNlsCallChain(usage, nonNlsTargets)) {
      return true;
    }

    if (isSafeStringMethod(usage, nonNlsTargets)) {
      return true;
    }

    if (isPassedToNonNls(usage, nonNlsTargets)) {
      return true;
    }

    if (ignoreForAssertStatements && isArgOfAssertStatement(usage)) {
      return true;
    }
    if (ignoreForExceptionConstructors && isExceptionArgument(usage)) {
      return true;
    }
    if (ignoreForEnumConstants && isArgOfEnumConstant(usage)) {
      return true;
    }
    if (!ignoreForExceptionConstructors && isArgOfSpecifiedExceptionConstructor(usage, ignoreForSpecifiedExceptionConstructors.split(","))) {
      return true;
    }
    if (ignoreForJUnitAsserts && isArgOfJUnitAssertion(usage)) {
      return true;
    }
    if (ignoreForClassReferences && value != null && isClassRef(usage, value)) {
      return true;
    }
    if (ignoreForPropertyKeyReferences && value != null && !PropertiesImplUtil.findPropertiesByKey(project, value).isEmpty()) {
      return true;
    }
    if (ignoreToString && isToString(usage)) {
      return true;
    }
    return false;
  }

  private static boolean isArgOfEnumConstant(UExpression expression) {
    return expression.getUastParent() instanceof UEnumConstant;
  }

  public void setNonNlsCommentPattern(String pattern) {
    nonNlsCommentPattern = pattern;
    myCachedCommentPattern = null;
    if (!pattern.trim().isEmpty()) {
      try {
        myCachedCommentPattern = Pattern.compile(pattern);
      }
      catch (PatternSyntaxException ignored) { }
    }
  }

  public void setNonNlsLiteralPattern(String pattern) {
    nonNlsLiteralPattern = pattern;
    myCachedLiteralPattern = null;
    if (!pattern.trim().isEmpty()) {
      try {
        myCachedLiteralPattern = Pattern.compile(pattern);
      }
      catch (PatternSyntaxException ignored) { }
    }
  }

  private static boolean isClassRef(final UExpression expression, String value) {
    if (StringUtil.startsWithChar(value, '#')) {
      value = value.substring(1); // A favor for JetBrains team to catch common Logger usage practice.
    }

    Project project = Objects.requireNonNull(expression.getSourcePsi()).getProject();
    return JavaPsiFacade.getInstance(project).findClass(value, GlobalSearchScope.allScope(project)) != null ||
           ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), value) != null;
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

  private boolean isPassedToNonNls(@NotNull UExpression expression,
                                   final Set<? super PsiModifierListOwner> nonNlsTargets) {
    NlsInfo info = NlsInfo.forExpression(JavaI18nUtil.getTopLevelExpression(expression, false));
    if (info == NlsInfo.nonLocalized()) return true;
    if (info instanceof NlsInfo.Unspecified) {
      PsiModifierListOwner candidate = ((NlsInfo.Unspecified)info).getAnnotationCandidate();
      if (candidate instanceof PsiVariable &&
          ignoreAssignedToConstants &&
          candidate.hasModifierProperty(PsiModifier.STATIC) &&
          candidate.hasModifierProperty(PsiModifier.FINAL)) {
        return true;
      }
      ContainerUtil.addIfNotNull(nonNlsTargets, candidate);
    }
    return false;
  }

  private static boolean annotatedAsNonNls(@NotNull PsiModifierListOwner parent) {
    return NlsInfo.forModifierListOwner(parent).getNlsStatus() == ThreeState.NO;
  }

  private static boolean isSafeStringMethod(UExpression expression, final Set<? super PsiModifierListOwner> nonNlsTargets) {
    UElement parent = UastUtils.skipParenthesizedExprUp(expression.getUastParent());
    if (!(parent instanceof UQualifiedReferenceExpression)) return false;
    UExpression selector = ((UQualifiedReferenceExpression)parent).getSelector();
    if (!(selector instanceof UCallExpression)) return false;
    UCallExpression call = (UCallExpression)selector;
    if (STRING_EQUALS.uCallMatches(call)) {
      final List<UExpression> expressions = call.getValueArguments();
      if (expressions.size() != 1) return false;
      final UExpression arg = UastUtils.skipParenthesizedExprDown(expressions.get(0));
      UReferenceExpression ref = ObjectUtils.tryCast(arg, UReferenceExpression.class);
      if (ref != null) {
        return isNonNlsCall(ref, nonNlsTargets);
      }
      return false;
    }
    if (STRING_LENGTH.uCallMatches(call)) {
      return true;
    }
    return false;
  }

  private static boolean isInNonNlsCallChain(@NotNull UExpression expression,
                                             final Set<? super PsiModifierListOwner> nonNlsTargets) {
    UExpression parent = UastUtils.skipParenthesizedExprDown(JavaI18nUtil.getTopLevelExpression(expression, true));
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
        PsiElement variableJavaPsi = newVariable.getJavaPsi();
        if (variableJavaPsi instanceof PsiModifierListOwner) {
          nonNlsTargets.add(((PsiModifierListOwner)variableJavaPsi));
        }
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
    ULocalVariable uVar = UastContextKt.toUElement(resolved, ULocalVariable.class);
    if (uVar != null) {
      UExpression initializer = uVar.getUastInitializer();
      if (initializer instanceof UCallExpression) {
        PsiMethod method = ((UCallExpression)initializer).resolve();
        if (method != null) {
          if (annotatedAsNonNls(method)) return true;
          nonNlsTargets.add(method);
        }
      }
    }
    if (qualifier instanceof UQualifiedReferenceExpression) {
      UExpression receiver = UastUtils.skipParenthesizedExprDown(((UQualifiedReferenceExpression)qualifier).getReceiver());
      if (receiver instanceof UReferenceExpression) {
        return isNonNlsCall((UReferenceExpression)receiver, nonNlsTargets);
      }
    }
    return false;
  }

  private static boolean isToString(final UExpression expression) {
    final UMethod method = UastUtils.getParentOfType(expression, UMethod.class);
    if (method == null) return false;
    final PsiType returnType = method.getReturnType();
    return TO_STRING.equals(method.getName())
           && method.getUastParameters().isEmpty()
           && returnType != null
           && "java.lang.String".equals(returnType.getCanonicalText());
  }

  private static boolean isArgOfJUnitAssertion(UExpression expression) {
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

  private static boolean isArgOfSpecifiedExceptionConstructor(UExpression expression,
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
          PsiMethod ctor = newExpression.resolve();
          return ctor != null && InheritanceUtil.isInheritor(ctor.getContainingClass(), CommonClassNames.JAVA_LANG_THROWABLE);
      }
    }
    return false;
  }

  private static class MarkAsSafeFix extends AddAnnotationFix implements LowPriorityAction {
    MarkAsSafeFix(PsiModifierListOwner target) {
      super(NlsInfo.NLS_SAFE, target, AnnotationUtil.NON_NLS);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaI18nBundle.message("intention.family.name.mark.as.nlssafe");
    }
  }
}
