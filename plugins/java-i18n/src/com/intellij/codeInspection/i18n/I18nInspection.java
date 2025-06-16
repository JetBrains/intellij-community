// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.externalAnnotation.NonNlsAnnotationProvider;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.AnnotationRequestsKt;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.intellij.lang.annotations.RegExp;
import org.jdom.Element;
import org.jetbrains.annotations.*;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.intellij.codeInspection.options.OptPane.*;

public final class I18nInspection extends AbstractBaseUastLocalInspectionTool implements CustomSuppressableInspectionTool {
  private static final CallMatcher ERROR_WRAPPER_METHODS = CallMatcher.anyOf(
    CallMatcher.staticCall("kotlin.PreconditionsKt__PreconditionsKt", "error").parameterCount(1),
    CallMatcher.staticCall("kotlin.StandardKt__StandardKt", "TODO").parameterCount(1)
  );
  private static final Set<UastBinaryOperator> STRING_COMPARISON_OPS =
    Set.of(UastBinaryOperator.EQUALS, UastBinaryOperator.NOT_EQUALS, UastBinaryOperator.IDENTITY_EQUALS,
           UastBinaryOperator.IDENTITY_NOT_EQUALS);

  private static final CallMatcher IGNORED_METHODS = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf").parameterTypes("int"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf").parameterTypes("double"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf").parameterTypes("long"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf").parameterTypes("char"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "toString"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "toString"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "toString"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_IO_FILE, "getAbsolutePath", "getCanonicalPath", "getName", "getPath"),
    CallMatcher.instanceCall("java.nio.file.Path", "toString"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_THROWABLE, "getMessage", "getLocalizedMessage").parameterCount(0),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_THROWABLE, "toString").parameterCount(0),
    CallMatcher.instanceCall("javax.swing.text.JTextComponent", "getText").parameterCount(0)
  );
  private static final CallMatcher STRING_BUILDER_TO_STRING = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING_BUFFER, "toString").parameterCount(0),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING_BUILDER, "toString").parameterCount(0));
  @RegExp private static final String DEFAULT_NON_NLS_LITERAL_PATTERN = "((?i)https?://.+)|\\w*(\\.\\w+)+|\\w*[$]\\w*|((?i)</?(html|b|i|body|br|li|ol|ul|code)/?>)*|&\\w+;|[A-Za-z][a-z0-9]*([A-Z]+[a-z0-9]*)+";
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
  private @NlsSafe String nonNlsLiteralPattern;
  public @NlsSafe String nonNlsCommentPattern;
  private boolean ignoreForEnumConstants;

  private @Nullable Pattern myCachedCommentPattern;
  private @Nullable Pattern myCachedLiteralPattern;
  private static final @NonNls String TO_STRING = "toString";

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
                        .setAttribute("value", nonNlsLiteralPattern));
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
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.internationalization.issues");
  }

  @Override
  public @NotNull String getShortName() {
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
  public @NotNull OptPane getOptionsPane() {
    return pane(
      tabs(
        tab(JavaI18nBundle.message("inspection.i18n.option.tab.general"),
            checkbox("ignoreForAllButNls", JavaI18nBundle.message("inspection.i18n.option.ignore.nls"))
              .description(JavaI18nBundle.message("inspection.i18n.option.ignore.nls.description")),
            checkbox("reportUnannotatedReferences", JavaI18nBundle.message("inspection.i18n.option.report.unannotated.refs"))
              .description(JavaI18nBundle.message("inspection.i18n.option.report.unannotated.refs.description")),
            expandableString("nonNlsCommentPattern", JavaI18nBundle.message("inspection.i18n.option.suppression.comment"), "\n")
              .description(JavaI18nBundle.message("inspection.i18n.option.suppression.comment.description"))
        ),
        tab(JavaI18nBundle.message("inspection.i18n.option.tab.context"),
            group(JavaI18nBundle.message("inspection.i18n.option.ignore.context"),
                  checkbox("ignoreForAssertStatements", JavaI18nBundle.message("inspection.i18n.option.ignore.context.assert"))
                    .description(exampleDescription("assert s.equals(\"Message\");")),
                  checkbox("ignoreForJUnitAsserts", JavaI18nBundle.message("inspection.i18n.option.ignore.context.junit.assert"))
                    .description(exampleDescription("assertEquals(s, \"Message\");")),
                  checkbox("ignoreAssignedToConstants", JavaI18nBundle.message(
                    "inspection.i18n.option.ignore.context.assigned.to.constants"))
                    .description(exampleDescription("static final ID = \"Message\"")),
                  checkbox("ignoreToString", JavaI18nBundle.message("inspection.i18n.option.ignore.context.tostring"))
                    .description(exampleDescription("""
                      public String toString() {
                        return "MyObject: value = " + value;
                      }""")),
                  checkbox("ignoreForEnumConstants", JavaI18nBundle.message("inspection.i18n.option.ignore.context.enum"))
                    .description(exampleDescription("""
                      enum MyEnum {
                        VALUE("Message");
                        MyEnum(String msg) {}
                      }""")),
                  checkbox("ignoreForExceptionConstructors", JavaI18nBundle.message(
                             "inspection.i18n.option.ignore.context.exception.constructor"),
                     stringList("ignoreForSpecifiedExceptionConstructors", "",
                                new JavaClassValidator().withSuperClass(CommonClassNames.JAVA_LANG_THROWABLE)))
            )
        ),
        tab(JavaI18nBundle.message("inspection.i18n.option.tab.literals"),
            group(JavaI18nBundle.message("inspection.i18n.option.no.report.content"),
                  checkbox("ignoreForClassReferences", JavaI18nBundle.message(
                    "inspection.i18n.option.no.report.content.qualified.class.names")),
                  checkbox("ignoreForPropertyKeyReferences", JavaI18nBundle.message(
                    "inspection.i18n.option.no.report.content.property.keys")),
                  checkbox("ignoreForNonAlpha", JavaI18nBundle.message("inspection.i18n.option.no.report.content.nonalphanumerics")),
                  expandableString("nonNlsLiteralPattern", JavaI18nBundle.message("inspection.i18n.option.no.report.content.string.pattern"),
                                   "\n")
                  )
        )
      )
    );
  }

  private static @NotNull HtmlChunk exampleDescription(@NlsSafe String exampleText) {
    return HtmlChunk.fragment(
      HtmlChunk.text(JavaI18nBundle.message("tooltip.example")),
      HtmlChunk.br(),
      HtmlChunk.tag("pre").addText(exampleText)
    );
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController()
      .onValue("ignoreForSpecifiedExceptionConstructors", () -> StringUtil.split(ignoreForSpecifiedExceptionConstructors, ","),
               res -> ignoreForSpecifiedExceptionConstructors = StringUtil.join((List<?>)res, ",")) 
      .onValueSet("nonNlsCommentPattern", pattern -> setNonNlsCommentPattern(pattern.toString()))
      .onValueSet("nonNlsLiteralPattern", pattern -> setNonNlsLiteralPattern(pattern.toString()));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    final PsiDirectory directory = holder.getFile().getContainingDirectory();
    if (directory != null && isPackageNonNls(JavaDirectoryService.getInstance().getPackage(directory))) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new StringI18nVisitor(holder, isOnTheFly), getHints());
  }

  @SuppressWarnings("unchecked")
  private Class<? extends UElement>[] getHints() {
    return reportUnannotatedReferences ?
           new Class[] {UInjectionHost.class, UCallExpression.class, UReferenceExpression.class} :
           new Class[] {UInjectionHost.class};
  }

  @Override
  public @NotNull String getAlternativeID() {
    return "nls";
  }

  private static @NotNull LocalQuickFix createIntroduceConstantFix() {
    return new IntroduceConstantFix();
  }

  private static @Nullable ULocalVariable getVariableToSearch(UExpression passThrough) {
    UElement uastParent = passThrough.getUastParent();
    ULocalVariable uVar = null;
    if (uastParent instanceof ULocalVariable) {
      uVar = (ULocalVariable)uastParent;
    }
    else if (uastParent instanceof UBinaryExpression &&
             (((UBinaryExpression)uastParent).getOperator() == UastBinaryOperator.ASSIGN ||
              ((UBinaryExpression)uastParent).getOperator() == UastBinaryOperator.PLUS_ASSIGN) &&
             AnnotationContext.expressionsAreEquivalent(((UBinaryExpression)uastParent).getRightOperand(), passThrough)) {
      UExpression left = ((UBinaryExpression)uastParent).getLeftOperand();
      if (left instanceof UResolvable) {
        PsiElement target = ((UResolvable)left).resolve();
        uVar = ObjectUtils.tryCast(UastContextKt.toUElement(target), ULocalVariable.class);
        if (uVar == null && target != null) {
          uVar = ObjectUtils.tryCast(UastContextKt.toUElement(target.getParent()), ULocalVariable.class);
        }
      }
    }
    return uVar;
  }

  private static @Nullable @Unmodifiable List<@NotNull UExpression> findUsages(UExpression passThrough, ULocalVariable uVar) {
    PsiElement psiVar = uVar.getSourcePsi();
    PsiElement psi = passThrough.getSourcePsi();
    if (psi != null && psiVar != null) {
      if (psiVar instanceof PsiLocalVariable local) {
        // Java
        List<PsiReferenceExpression> refs = VariableAccessUtils.getVariableReferences(local);
        return ContainerUtil.mapNotNull(
          refs, ref -> PsiUtil.isAccessedForWriting(ref) ? null : UastContextKt.toUElement(ref, UExpression.class));
      }
      else {
        // Kotlin
        Collection<PsiReference> refs = ReferencesSearch.search(psiVar, psiVar.getUseScope()).findAll();
        return ContainerUtil.mapNotNull(refs, ref -> {
          UExpression expr = UastContextKt.toUElement(ref.getElement(), UExpression.class);
          if (expr != null && expr.getUastParent() instanceof UBinaryExpression &&
              AnnotationContext.expressionsAreEquivalent(((UBinaryExpression)expr.getUastParent()).getLeftOperand(), expr)) {
            return null;
          }
          return expr;
        });
      }
    }
    return null;
  }

  private final class StringI18nVisitor extends AbstractUastNonRecursiveVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myOnTheFly;

    private StringI18nVisitor(@NotNull ProblemsHolder holder, boolean onTheFly) {
      myHolder = holder;
      myOnTheFly = onTheFly;
    }

    @Override
    public boolean visitCallExpression(@NotNull UCallExpression ref) {
      PsiElement sourcePsi = ref.getSourcePsi();
      if (sourcePsi == null) return true;
      PsiMethod target = ref.resolve();
      if (target == null) return true;
      if (IGNORED_METHODS.methodMatches(target)) return true;
      if (!target.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = target.getContainingClass();
        if (containingClass != null && (CommonClassNames.JAVA_LANG_STRING.equals(containingClass.getQualifiedName()) ||
                                        CommonClassNames.JAVA_LANG_CHAR_SEQUENCE.equals(containingClass.getQualifiedName()))) {
          return true;
        }
      }
      if (ref.getUastParent() instanceof UQualifiedReferenceExpression parent) {
        if (STRING_BUILDER_TO_STRING.methodMatches(target)) {
          UExpression receiver = parent.getReceiver();
          if (receiver instanceof UResolvable) {
            PsiElement receiverTarget = ((UResolvable)receiver).resolve();
            if (receiverTarget instanceof PsiModifierListOwner) {
              if (NlsInfo.forModifierListOwner((PsiModifierListOwner)receiverTarget).canBeUsedInLocalizedContext()) return false;
            }
            ULocalVariable uVar = UastContextKt.toUElement(receiverTarget, ULocalVariable.class);
            if (uVar != null && NlsInfo.fromUVariable(uVar).canBeUsedInLocalizedContext()) return false;
          }
        }
      }
      if (MethodUtils.isToString(target) && PsiPrimitiveType.getUnboxedType(ref.getReceiverType()) != null) {
        // toString() on primitive: consider safe
        return true;
      }
      processReferenceToNonLocalized(sourcePsi, ref, target);
      return true;
    }

    @Override
    public boolean visitQualifiedReferenceExpression(@NotNull UQualifiedReferenceExpression ref) {
      return visitReference(ref);
    }

    @Override
    public boolean visitSimpleNameReferenceExpression(@NotNull USimpleNameReferenceExpression ref) {
      return visitReference(ref);
    }

    private boolean visitReference(@NotNull UReferenceExpression ref) {
      PsiElement sourcePsi = ref.getSourcePsi();
      if (sourcePsi == null) return true;
      PsiVariable target = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
      if (target == null || target instanceof PsiLocalVariable) return true;
      processReferenceToNonLocalized(sourcePsi, ref, target);
      return true;
    }

    private void processReferenceToNonLocalized(@NotNull PsiElement sourcePsi, @NotNull UExpression ref, PsiModifierListOwner target) {
      PsiType type = ref.getExpressionType();
      if (!TypeUtils.isJavaLangString(type) && !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_CHAR_SEQUENCE, type)) return;
      if (target instanceof PsiMethod &&
          (StringFlowUtil.isStringProcessingMethod((PsiMethod)target, NlsInfo.factory()) || StringFlowUtil.isPassthroughMethod((PsiMethod)target, null, null, NlsInfo.factory()))) {
        return;
      }
      if (NlsInfo.forModifierListOwner(target).canBeUsedInLocalizedContext()) return;
      if (NlsInfo.forType(type).canBeUsedInLocalizedContext()) return;

      String value = target instanceof PsiVariable ? ObjectUtils.tryCast(((PsiVariable)target).computeConstantValue(), String.class) : null;

      NlsInfo targetInfo = getExpectedNlsInfo(myHolder.getProject(), ref, value, new HashSet<>(), myOnTheFly, true);
      if (targetInfo instanceof NlsInfo.Localized) {
        List<LocalQuickFix> fixes = new ArrayList<>();
        String fqn = ((NlsInfo.Localized)targetInfo).suggestAnnotation(target);
        ExternalAnnotationsManager.AnnotationPlace annotationPlace = AddAnnotationPsiFix.choosePlace(fqn, target);
        boolean addNullSafe = JavaPsiFacade.getInstance(target.getProject()).findClass(NlsInfo.NLS_SAFE, target.getResolveScope()) != null;
        if (annotationPlace == ExternalAnnotationsManager.AnnotationPlace.IN_CODE && target instanceof JvmModifiersOwner) {
          fixes.addAll(IntentionWrapper.wrapToQuickFixes(JvmElementActionFactories.createAddAnnotationActions((JvmModifiersOwner)target, AnnotationRequestsKt.annotationRequest(fqn)), sourcePsi.getContainingFile()));
          if (addNullSafe) {
            for (IntentionAction action : JvmElementActionFactories.createAddAnnotationActions((JvmModifiersOwner)target, AnnotationRequestsKt.annotationRequest(NlsInfo.NLS_SAFE))) {
              fixes.add(new IntentionWrapper(action) {
                @Override
                public @NotNull String getFamilyName() {
                  return JavaI18nBundle.message("intention.family.name.mark.as.nlssafe");
                }
              });
            }
          }
        }
        else {
          fixes.add(LocalQuickFix.from(new AddAnnotationModCommandAction(fqn, target, AnnotationUtil.NON_NLS)));
          if (addNullSafe) {
            fixes.add(LocalQuickFix.from(new AddAnnotationModCommandAction(NlsInfo.NLS_SAFE, target, AnnotationUtil.NON_NLS)
                                           .withPresentation(presentation -> presentation.withPriority(PriorityAction.Priority.LOW))));
          }
        }
        String description = JavaI18nBundle.message("inspection.i18n.message.non.localized.passed.to.localized");
        myHolder.registerProblem(sourcePsi, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    }

    @Override
    public boolean visitElement(@NotNull UElement node) {
      if (node instanceof UInjectionHost) {
        PsiElement psi = node.getSourcePsi();
        if (psi != null) {
          visitLiteralExpression(psi, (UInjectionHost)node);
        }
      }
      return super.visitElement(node);
    }

    private void visitLiteralExpression(@NotNull PsiElement sourcePsi, @NotNull UInjectionHost expression) {
      String stringValue = getStringValueOfKnownPart(expression);
      if (StringUtil.isEmptyOrSpaces(stringValue)) {
        return;
      }

      Set<PsiModifierListOwner> nonNlsTargets = new HashSet<>();
      NlsInfo info = getExpectedNlsInfo(myHolder.getProject(), expression, stringValue, nonNlsTargets, myOnTheFly, ignoreForAllButNls);
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

      if (sourcePsi instanceof PsiLiteralExpression && PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, sourcePsi)) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(myHolder.getProject());
        for (PsiModifierListOwner element : nonNlsTargets) {
          if (NlsInfo.forModifierListOwner(element).getNlsStatus() == ThreeState.UNSURE) {
            if (!element.getManager().isInProject(element) ||
                facade.findClass(AnnotationUtil.NON_NLS, element.getResolveScope()) != null) {
              fixes.add(LocalQuickFix.from(new NonNlsAnnotationProvider().createFix(element)));
            }
          }
        }
      }

      if (!(sourcePsi instanceof PsiLiteralExpression) || !isSwitchCase(expression)) {
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
      }

      LocalQuickFix[] farr = fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
      myHolder.registerProblem(sourcePsi, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, farr);
    }

    private static boolean isSwitchCase(@NotNull UInjectionHost expression) {
      if (expression.getUastParent() instanceof USwitchClauseExpression parent) {
        return ContainerUtil.exists(parent.getCaseValues(), value -> expression.equals(value));
      }
      return false;
    }

    private static boolean isNotConstantFieldInitializer(final PsiExpression expression) {
      PsiField parentField = expression.getParent() instanceof PsiField ? (PsiField)expression.getParent() : null;
      return parentField != null && expression == parentField.getInitializer() &&
             parentField.hasModifierProperty(PsiModifier.FINAL) &&
             parentField.hasModifierProperty(PsiModifier.STATIC);
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

  static List<UExpression> findIndirectUsages(UExpression expression, boolean allowStringModifications) {
    UExpression passThrough = StringFlowUtil.goUp(expression, allowStringModifications, NlsInfo.factory());
    ULocalVariable uVar = getVariableToSearch(passThrough);
    if (uVar != null && NlsInfo.fromUVariable(uVar).getNlsStatus() == ThreeState.UNSURE) {
      List<UExpression> usages = findUsages(passThrough, uVar);
      if (usages != null) return usages;
    }
    return Collections.singletonList(passThrough);
  }

  private NlsInfo getExpectedNlsInfo(@NotNull Project project,
                                     @NotNull UExpression expression,
                                     @Nullable String value,
                                     @NotNull Set<? super PsiModifierListOwner> nonNlsTargets,
                                     boolean onTheFly,
                                     boolean ignoreForAllButNls) {
    if (ignoreForNonAlpha && value != null && !StringUtil.containsAlphaCharacters(value)) {
      return NlsInfo.nonLocalized();
    }

    if (isSuppressedByComment(project, expression)) {
      return NlsInfo.nonLocalized();
    }

    if (value != null && myCachedLiteralPattern != null && myCachedLiteralPattern.matcher(value).matches()) {
      return NlsInfo.nonLocalized();
    }

    List<UExpression> usages = findIndirectUsages(expression, true);
    if (usages.isEmpty()) {
      usages = Collections.singletonList(expression);
    }

    for (UExpression usage : usages) {
      NlsInfo info = NlsInfo.forExpression(usage);
      switch (info.getNlsStatus()) {
        case YES -> {
          return info;
        }
        case UNSURE -> {
          if (ignoreForAllButNls) {
            break;
          }
          if (shouldIgnoreUsage(project, value, nonNlsTargets, usage)) {
            break;
          }
          if (!onTheFly) { //keep only potential annotation candidate
            nonNlsTargets.clear();
          }
          ContainerUtil.addIfNotNull(nonNlsTargets, ((NlsInfo.NlsUnspecified)info).getAnnotationCandidate());
          return NlsInfo.localized();
        }
        case NO -> {}
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
    if (ignoreForClassReferences && value != null && isClassRef(project, value)) {
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

  private static boolean isClassRef(@NotNull Project project,
                                    String value) {
    if (StringUtil.startsWithChar(value, '#')) {
      value = value.substring(1); // A favor for JetBrains team to catch common Logger usage practice.
    }

    return JavaPsiFacade.getInstance(project).findClass(value, GlobalSearchScope.allScope(project)) != null ||
           ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), value) != null;
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
    if (info.getNlsStatus() == ThreeState.NO) return true;
    if (info instanceof NlsInfo.NlsUnspecified) {
      PsiModifierListOwner candidate = ((NlsInfo.NlsUnspecified)info).getAnnotationCandidate();
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
    if (parent instanceof UBinaryExpression binOp) {
      if (STRING_COMPARISON_OPS.contains(binOp.getOperator())) {
        UResolvable left = ObjectUtils.tryCast(UastUtils.skipParenthesizedExprDown(binOp.getLeftOperand()), UResolvable.class);
        UResolvable right = ObjectUtils.tryCast(UastUtils.skipParenthesizedExprDown(binOp.getRightOperand()), UResolvable.class);
        return left != null && isNonNlsCall(left, nonNlsTargets) ||
               right != null && isNonNlsCall(right, nonNlsTargets);
      }
    }
    if (!(parent instanceof UQualifiedReferenceExpression)) return false;
    UExpression selector = ((UQualifiedReferenceExpression)parent).getSelector();
    if (!(selector instanceof UCallExpression call)) return false;
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
    if (parent instanceof UResolvable && isNonNlsCall((UResolvable)parent, nonNlsTargets)) {
      return true;
    }
    if (UastExpressionUtils.isAssignment(parent)) {
      UExpression operand = ((UBinaryExpression)parent).getLeftOperand();
      if (operand instanceof UReferenceExpression &&
          isNonNlsCall((UReferenceExpression)operand, nonNlsTargets)) return true;
    }
    if (parent instanceof UCallExpression) {
      UElement parentOfNew = UastUtils.skipParenthesizedExprUp(parent.getUastParent());
      if (parentOfNew instanceof ULocalVariable newVariable) {
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

  private static boolean isNonNlsCall(UResolvable qualifier, Set<? super PsiModifierListOwner> nonNlsTargets) {
    final PsiElement resolved = qualifier.resolve();
    if (resolved instanceof PsiModifierListOwner modifierListOwner) {
      if (annotatedAsNonNls(modifierListOwner)) {
        return true;
      }
      nonNlsTargets.add(modifierListOwner);
    }
    ULocalVariable uVar = UastContextKt.toUElement(resolved, ULocalVariable.class);
    if (uVar != null) {
      if (NlsInfo.fromUVariable(uVar).getNlsStatus() == ThreeState.NO) return true;
      UExpression initializer = uVar.getUastInitializer();
      if (initializer instanceof UResolvable) {
        PsiModifierListOwner method = ObjectUtils.tryCast(((UResolvable)initializer).resolve(), PsiModifierListOwner.class);
        if (method != null) {
          if (annotatedAsNonNls(method)) return true;
          nonNlsTargets.add(method);
        }
      }
    }
    if (qualifier instanceof UQualifiedReferenceExpression) {
      UExpression receiver = UastUtils.skipParenthesizedExprDown(((UQualifiedReferenceExpression)qualifier).getReceiver());
      if (receiver instanceof UResolvable) {
        return isNonNlsCall((UResolvable)receiver, nonNlsTargets);
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
    final @NonNls String methodName = ((UCallExpression)parent).getMethodName();
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
    return InheritanceUtil.isInheritor(containingClass,JUnitCommonClassNames.ORG_JUNIT_ASSERT) ||
           InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS) ||
           InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT);
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
        UReferenceExpression classReference = newExpression.getClassReference();
        if (classReference != null) {
          PsiClass cls = ObjectUtils.tryCast(classReference.resolve(), PsiClass.class);
          if (cls != null) {
            return InheritanceUtil.isInheritor(cls, CommonClassNames.JAVA_LANG_THROWABLE);
          }
        }
      }
      PsiMethod method = newExpression.resolve();
      if (method != null) {
        if (method.isConstructor()) {
          return InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_LANG_THROWABLE);
        }
        return ERROR_WRAPPER_METHODS.methodMatches(method);
      }
    }
    return false;
  }
}
