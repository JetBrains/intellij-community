// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.openapi.util.NlsContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nls.Capitalization;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.util.UastExpressionUtils;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Contains information about localization status.
 * The class has three implementations {@link Localized}, {@link NonLocalized} and {@link Unspecified},
 * which may provide additional information.
 */
public abstract class NlsInfo {
  private static final @NotNull String NLS_CONTEXT = "com.intellij.openapi.util.NlsContext";
  static final String NLS_SAFE = "com.intellij.openapi.util.NlsSafe";
  private static final @NotNull Set<String> ANNOTATION_NAMES = Set.of(AnnotationUtil.NLS, AnnotationUtil.NON_NLS, NLS_SAFE);

  /**
   * Describes a string that should be localized
   */
  public static final class Localized extends NlsInfo {
    private static final Localized NLS = new Localized(Capitalization.NotSpecified, "", "", null);
    private static final Localized NLS_TITLE = new Localized(Capitalization.Title, "", "", null);
    private static final Localized NLS_SENTENCE = new Localized(Capitalization.Sentence, "", "", null);
    private final @NotNull Capitalization myCapitalization;
    private final @NotNull @NonNls String myPrefix;
    private final @NotNull @NonNls String mySuffix;
    private final String myAnnotationName;

    private Localized(@NotNull Capitalization capitalization,
                      @NotNull @NonNls String prefix,
                      @NotNull @NonNls String suffix,
                      @Nullable @NonNls String annotationName) {
      super(ThreeState.YES);
      myCapitalization = capitalization;
      myPrefix = prefix;
      mySuffix = suffix;
      myAnnotationName = annotationName;
    }

    /**
     * @return expected string capitalization
     * @see Nls#capitalization()
     */
    public @NotNull Capitalization getCapitalization() {
      return myCapitalization;
    }
    
    public @NotNull String suggestAnnotation(PsiElement context) {
      if (myAnnotationName != null &&
          JavaPsiFacade.getInstance(context.getProject()).findClass(myAnnotationName, context.getResolveScope()) != null) {
        return myAnnotationName;
      }
      return AnnotationUtil.NLS;
    }

    /**
     * @return desired prefix for new property keys
     * @see NlsContext#prefix()
     */
    public @NotNull @NonNls String getPrefix() {
      return myPrefix;
    }

    /**
     * @return desired suffix for new property keys
     * @see NlsContext#suffix()
     */
    public @NotNull @NonNls String getSuffix() {
      return mySuffix;
    }

    private @NotNull Localized withPrefixAndSuffix(@NotNull String prefix, @NotNull String suffix) {
      if (prefix.equals(myPrefix) && suffix.equals(mySuffix)) {
        return this;
      }
      return new Localized(myCapitalization, prefix, suffix, myAnnotationName);
    }

    private @NotNull Localized withAnnotation(@NotNull UAnnotation annotation) {
      String qualifiedName = annotation.getQualifiedName();
      if (Objects.equals(qualifiedName, myAnnotationName)) {
        return this;
      }
      return new Localized(myCapitalization, myPrefix, mySuffix, qualifiedName);
    }
  }

  /**
   * Describes a string that should not be localized but it's still safe to be displayed in UI
   * (e.g. file name).
   */
  public static final class NlsSafe extends NlsInfo {
    private static final NlsSafe INSTANCE = new NlsSafe();

    private NlsSafe() {super(ThreeState.NO);}
  }

  /**
   * Describes a string that should not be localized
   */
  public static final class NonLocalized extends NlsInfo {
    private static final NonLocalized INSTANCE = new NonLocalized();

    private NonLocalized() {super(ThreeState.NO);}
  }

  /**
   * Describes a string, whose localization status is not explicitly specified.
   * Whether the string should be localized or not may depend on the user settings and various heuristics.
   */
  public static final class Unspecified extends NlsInfo {
    private static final Unspecified UNKNOWN = new Unspecified(null);

    private final @Nullable PsiModifierListOwner myCandidate;

    private Unspecified(@Nullable PsiModifierListOwner candidate) {
      super(ThreeState.UNSURE);
      myCandidate = candidate;
    }

    /**
     * @return a place where it's desired to put an explicit {@link Nls} or {@link NonNls} annotation.
     * May return null if such kind of a place cannot be determined.
     */
    public @Nullable PsiModifierListOwner getAnnotationCandidate() {
      return myCandidate;
    }
  }

  private final @NotNull ThreeState myNls;

  private NlsInfo(@NotNull ThreeState nls) {
    myNls = nls;
  }

  /**
   * @return {@link ThreeState#YES} if the string must be localized (see {@link Localized});<br>
   * {@link ThreeState#NO} if the string must not be localized (see {@link NonLocalized});<br>
   * {@link ThreeState#UNSURE} if it's not explicitly specified (see {@link Unspecified});
   */
  public @NotNull ThreeState getNlsStatus() {
    return myNls;
  }

  /**
   * @return true if the element with given localization status can be used in localized context.
   */
  public boolean canBeUsedInLocalizedContext() {
    return this instanceof Localized || this instanceof NlsSafe;
  }
  
  /**
   * @return "localized" info object without specified capitalization, prefix and suffix
   */
  public static @NotNull Localized localized() {
    return Localized.NLS;
  }

  /**
   * @return "non-localized" info object
   */
  public static @NotNull NonLocalized nonLocalized() {
    return NonLocalized.INSTANCE;
  }

  /**
   * @param expression expression to determine the localization status for
   * @return localization status
   */
  public static @NotNull NlsInfo forExpression(@NotNull UExpression expression) {
    return forExpression(expression, true);
  }

  /**
   * @param expression expression to determine the localization status for
   * @param allowStringModifications whether string modifications are allowed
   * @return localization status
   */
  static @NotNull NlsInfo forExpression(@NotNull UExpression expression, boolean allowStringModifications) {
    expression = goUp(expression, allowStringModifications);
    AnnotationContext context = AnnotationContext.fromExpression(expression);
    return fromAnnotationContext(expression.getUastParent(), context);
  }

  public static @NotNull NlsInfo forType(@NotNull PsiType type) {
    return fromAnnotationOwner(type);
  }

  public static @NotNull NlsInfo forModifierListOwner(@NotNull PsiModifierListOwner owner) {
    return fromAnnotationContext(null, AnnotationContext.fromModifierListOwner(owner));
  }

  public static @NotNull Capitalization getCapitalization(@NotNull PsiModifierListOwner owner) {
    NlsInfo info = forModifierListOwner(owner);
    if (info instanceof Localized) {
      return ((Localized)info).getCapitalization();
    }
    return Capitalization.NotSpecified;
  }

  private static @NotNull NlsInfo fromAnnotationContext(UElement parent, AnnotationContext context) {
    NlsInfo info = fromType(context.getType());
    if (info != Unspecified.UNKNOWN) return info;
    PsiModifierListOwner owner = context.getOwner();
    if (owner == null) return Unspecified.UNKNOWN;
    info = fromAnnotationOwner(owner.getModifierList());
    if (info != Unspecified.UNKNOWN) return info;
    if (owner instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)owner;
      if (parameter.isVarArgs() && context.getType() instanceof PsiEllipsisType) {
        info = fromType(((PsiEllipsisType)context.getType()).getComponentType());
      }
    }
    else if (owner instanceof PsiVariable) {
      ULocalVariable uLocal = UastContextKt.toUElement(owner, ULocalVariable.class);
      if (uLocal != null) {
        info = fromUVariable(uLocal);
      }
    }
    if (info instanceof Unspecified) {
      info = context.secondaryItems().map(item -> fromAnnotationOwner(item.getModifierList()))
        .filter(inf -> inf != Unspecified.UNKNOWN).findFirst().orElse(info);
    }
    if (info == Unspecified.UNKNOWN) {
      PsiMember member =
        ObjectUtils.tryCast(owner instanceof PsiParameter ? ((PsiParameter)owner).getDeclarationScope() : owner, PsiMember.class);
      if (member != null) {
        info = fromContainer(member);
      }
    }
    if (info == Unspecified.UNKNOWN && (!(parent instanceof UCallExpression) || owner instanceof PsiParameter)) {
      info = new Unspecified(owner);
    }
    return info;
  }

  private static @NotNull NlsInfo fromType(PsiType type) {
    if (type == null) return Unspecified.UNKNOWN;
    Ref<NlsInfo> result = Ref.create(Unspecified.UNKNOWN);
    InheritanceUtil.processSuperTypes(type, true, eachType -> {
      NlsInfo info = fromAnnotationOwner(eachType);
      if (info != Unspecified.UNKNOWN) {
        result.set(info);
        return false;
      }
      return !(eachType instanceof PsiClassType) || PsiUtil.resolveClassInClassTypeOnly(eachType) instanceof PsiTypeParameter;
    });
    return result.get();
  }

  public static @NotNull UExpression goUp(@NotNull UExpression expression, boolean allowStringTransformation) {
    UExpression parent = expression;
    while (true) {
      UElement parentElement = parent.getUastParent();
      if (parentElement instanceof ULocalVariable && parentElement.getUastParent() instanceof UDeclarationsExpression) {
        // Kotlin has strange hierarchy for elvis operator
        UExpressionList elvis = ObjectUtils.tryCast(parentElement.getUastParent().getUastParent(), UExpressionList.class);
        if (elvis != null) {
          parentElement = elvis;
        }
      }
      if (parentElement instanceof UExpressionList) {
        UExpression lastExpression = ContainerUtil.getLastItem(((UExpressionList)parentElement).getExpressions());
        if (lastExpression != null && AnnotationContext.expressionsAreEquivalent(parent, lastExpression)) {
          // Result of expression list is the last expression in the list in Kotlin
          parentElement = parentElement.getUastParent();
        }
      }
      UExpression next = ObjectUtils.tryCast(parentElement, UExpression.class);
      if (next == null || next instanceof UNamedExpression) return parent;
      if (next instanceof USwitchClauseExpression) {
        if (((USwitchClauseExpression)next).getCaseValues().contains(AnnotationContext.normalize(parent))) return parent;
        UExpressionList switchBody = ObjectUtils.tryCast(next.getUastParent(), UExpressionList.class);
        if (switchBody == null) return parent;
        USwitchExpression switchExpression = ObjectUtils.tryCast(switchBody.getUastParent(), USwitchExpression.class);
        if (switchExpression == null) return parent;
        next = switchExpression;
      }
      ULambdaExpression lambda = ObjectUtils.tryCast(next, ULambdaExpression.class);
      if (next instanceof UReturnExpression) {
        lambda = ObjectUtils.tryCast(((UReturnExpression)next).getJumpTarget(), ULambdaExpression.class);
        if (lambda == null) return parent;
      }
      if (lambda != null) {
        UCallExpression uastParent = ObjectUtils.tryCast(lambda.getUastParent(), UCallExpression.class);
        if (uastParent == null) return parent;
        PsiMethod method = uastParent.resolve();
        if (method == null || !isPassthroughMethod(method, uastParent, lambda)) return parent;
        next = uastParent;
      }
      if (next instanceof UQualifiedReferenceExpression && !TypeUtils.isJavaLangString(next.getExpressionType())
          && !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_CHAR_SEQUENCE, next.getExpressionType())) {
        return parent;
      }
      if (next instanceof UPolyadicExpression &&
          (!allowStringTransformation || ((UPolyadicExpression)next).getOperator() != UastBinaryOperator.PLUS)) {
        return parent;
      }
      if (next instanceof UCallExpression) {
        if (!UastExpressionUtils.isArrayInitializer(next) && !UastExpressionUtils.isNewArrayWithInitializer(next)) {
          PsiMethod method = ((UCallExpression)next).resolve();
          boolean shouldGoThroughCall =
            TypeUtils.isJavaLangString(next.getExpressionType()) &&
            (allowStringTransformation && isStringProcessingMethod(method) ||
             isPassthroughMethod(method, (UCallExpression)next, parent));
          if (!shouldGoThroughCall) return parent;
        }
      }
      if (next instanceof UIfExpression && AnnotationContext.expressionsAreEquivalent(parent, ((UIfExpression)next).getCondition())) {
        return parent;
      }
      if (next instanceof USwitchExpression) {
        final UExpression condExpression = ((USwitchExpression)next).getExpression();
        if (condExpression != null && AnnotationContext.expressionsAreEquivalent(parent, condExpression)) {
          return parent;
        }
      }
      parent = next;
    }
  }

  static boolean isPassthroughMethod(@Nullable PsiMethod method, @Nullable UCallExpression call, @Nullable UExpression arg) {
    if (method == null) return false;
    PsiType type = method.getReturnType();
    PsiTypeParameter typeParameter = ObjectUtils.tryCast(PsiUtil.resolveClassInClassTypeOnly(type), PsiTypeParameter.class);
    if (typeParameter != null && typeParameter.getExtendsList().getReferencedTypes().length == 0) {
      PsiParameter[] parameters;
      if (arg == null || call == null) {
        parameters = method.getParameterList().getParameters();
      } else {
        PsiParameter parameter = AnnotationContext.getParameter(method, call, arg);
        if (parameter == null) return false;
        PsiType parameterType = parameter.getType();
        PsiElement psi = call.getSourcePsi();
        if (psi instanceof PsiMethodCallExpression) {
          PsiSubstitutor substitutor = ((PsiMethodCallExpression)psi).getMethodExpression().advancedResolve(false).getSubstitutor();
          parameterType = substitutor.substitute(parameterType);
        }
        if (fromType(parameterType) != Unspecified.UNKNOWN) return false;
        parameters = new PsiParameter[]{parameter};
      }
      for (PsiParameter parameter : parameters) {
        PsiType parameterType = parameter.getType();
        if (type.equals(GenericsUtil.getVariableTypeByExpressionType(parameterType))) return true;
        PsiType returnType = GenericsUtil.getVariableTypeByExpressionType(LambdaUtil.getFunctionalInterfaceReturnType(parameterType));
        if (type.equals(returnType)) return true;
      }
    }
    return isKotlinPassthroughMethod(method);
  }

  /**
   * Checks if the method is detected to be a string-processing method. A string processing method is a method that:
   * <ul>
   *   <li>Pure (either explicitly marked or inferred)</li>
   *   <li>Accepts parameters</li>
   *   <li>No parameters are marked using Nls annotations</li>
   *   <li>Return value is not marked using Nls annotations</li>
   * </ul>
   *
   * @param method                    method to check
   * @return true if method is detected to be a string-processing method. A string processing method is a method that:
   */
  static boolean isStringProcessingMethod(PsiMethod method) {
    if (method == null) return false;
    if (!(forModifierListOwner(method) instanceof Unspecified)) return false;
    if (!JavaMethodContractUtil.isPure(method)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0) return false;
    for (PsiParameter parameter : parameters) {
      if (!(forModifierListOwner(parameter) instanceof Unspecified)) return false;
    }
    return true;
  }

  private static boolean isKotlinPassthroughMethod(PsiMethod method) {
    if ((method.getName().equals("let") || method.getName().equals("run")) &&
        method.getModifierList().textMatches("public inline")) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 2 && isReceiver(method, parameters[0]) && parameters[1].getName().equals("block")) {
        return true;
      }
    }
    if (method.getName().equals("joinToString")) {
      PsiClass aClass = method.getContainingClass();
      return aClass != null && "kotlin.collections.CollectionsKt___CollectionsKt".equals(aClass.getQualifiedName());
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      PsiParameter parameter = method.getParameterList().getParameter(0);
      if (parameter != null && isReceiver(method, parameter)) {
        PsiClass aClass = method.getContainingClass();
        return aClass != null &&
               aClass.getQualifiedName() != null &&
               aClass.getQualifiedName().startsWith("kotlin.text.StringsKt__Strings");
      }
    }
    return false;
  }

  static @NotNull NlsInfo fromUVariable(@NotNull UVariable owner) {
    for (UAnnotation annotation : owner.getUAnnotations()) {
      NlsInfo info = fromAnnotation(annotation);
      if (info != Unspecified.UNKNOWN) {
        return info;
      }
      info = fromMetaAnnotation(annotation);
      if (info != Unspecified.UNKNOWN) {
        return info;
      }
    }
    return Unspecified.UNKNOWN;
  }

  private static @NotNull NlsInfo fromAnnotationOwner(@Nullable PsiAnnotationOwner owner) {
    if (owner == null) return Unspecified.UNKNOWN;
    if (owner instanceof PsiModifierList) {
      return CachedValuesManager.getCachedValue((PsiModifierList)owner, () ->
        CachedValueProvider.Result.create(computeFromAnnotationOwner(owner), PsiModificationTracker.MODIFICATION_COUNT));
    }
    return computeFromAnnotationOwner(owner);
  }

  private static @NotNull NlsInfo computeFromAnnotationOwner(@NotNull PsiAnnotationOwner owner) {
    for (PsiAnnotation annotation : owner.getAnnotations()) {
      NlsInfo info = fromAnnotation(annotation);
      if (info != Unspecified.UNKNOWN) {
        return info;
      }
      UAnnotation uAnnotation = UastContextKt.toUElement(annotation, UAnnotation.class);
      if (uAnnotation != null) {
        info = fromMetaAnnotation(uAnnotation);
        if (info != Unspecified.UNKNOWN) {
          return info;
        }
      }
    }
    if (owner instanceof PsiModifierList) {
      PsiElement parent = ((PsiModifierList)owner).getParent();
      if (parent instanceof PsiModifierListOwner) {
        // Could be externally annotated
        PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy((PsiModifierListOwner)parent, ANNOTATION_NAMES, false);
        if (annotation != null) {
          return fromAnnotation(annotation);
        }
      }
    }
    return Unspecified.UNKNOWN;
  }

  private static @NotNull NlsInfo fromMetaAnnotation(@NotNull UAnnotation annotation) {
    PsiClass annotationClass = annotation.resolve();
    if (annotationClass == null) return Unspecified.UNKNOWN;
    NlsInfo baseInfo = Unspecified.UNKNOWN;
    String prefix = "";
    String suffix = "";
    for (PsiAnnotation metaAnno : annotationClass.getAnnotations()) {
      if (metaAnno.hasQualifiedName(NLS_CONTEXT)) {
        prefix = StringUtil.notNullize(AnnotationUtil.getStringAttributeValue(metaAnno, "prefix"));
        suffix = StringUtil.notNullize(AnnotationUtil.getStringAttributeValue(metaAnno, "suffix"));
      }
      else {
        NlsInfo info = fromAnnotation(metaAnno);
        if (info != Unspecified.UNKNOWN) {
          baseInfo = info;
        }
      }
    }
    if (baseInfo instanceof Localized) {
      return ((Localized)baseInfo).withPrefixAndSuffix(prefix, suffix).withAnnotation(annotation);
    }
    return baseInfo;
  }

  private static @NotNull NlsInfo fromAnnotation(@NotNull PsiAnnotation annotation) {
    return fromAnnotationInfo(
      annotation.getQualifiedName(),
      () -> UastContextKt.toUElement(annotation.findAttributeValue("capitalization"), UExpression.class)
    );
  }

  private static @NotNull NlsInfo fromAnnotation(@NotNull UAnnotation annotation) {
    return fromAnnotationInfo(annotation.getQualifiedName(), () -> annotation.findAttributeValue("capitalization"));
  }

  private static @NotNull NlsInfo fromAnnotationInfo(String qualifiedName, Supplier<UExpression> capitalization) {
      if (qualifiedName == null) return Unspecified.UNKNOWN;
    if (qualifiedName.equals(AnnotationUtil.NON_NLS) ||
        qualifiedName.equals(AnnotationUtil.PROPERTY_KEY)) {
      return NonLocalized.INSTANCE;
    }
    if (qualifiedName.equals(NLS_SAFE) ||
        qualifiedName.equals("org.intellij.lang.annotations.RegExp")) {
      return NlsSafe.INSTANCE;
    }
    if (qualifiedName.equals(AnnotationUtil.NLS)) {
      UExpression value = capitalization.get();
      String name = null;
      if (value instanceof UReferenceExpression) {
        // Java plugin returns reference for enum constant in annotation value
        name = ((UReferenceExpression)value).getResolvedName();
      }
      if (name != null) {
        if (Capitalization.Title.name().equals(name)) {
          return Localized.NLS_TITLE;
        }
        if (Capitalization.Sentence.name().equals(name)) {
          return Localized.NLS_SENTENCE;
        }
      }
      return Localized.NLS;
    }
    return Unspecified.UNKNOWN;
  }

  private static boolean isReceiver(PsiMethod method, PsiParameter param) {
    return param.getName().equals("$receiver") || param.getName().equals("$this$" + method.getName());
  }

  private static @NotNull NlsInfo fromContainer(@NotNull PsiMember member) {
    // From class
    PsiClass containingClass = member.getContainingClass();
    while (containingClass != null) {
      NlsInfo classInfo = fromAnnotationOwner(containingClass.getModifierList());
      if (classInfo != Unspecified.UNKNOWN) {
        return classInfo;
      }
      containingClass = containingClass.getContainingClass();
    }

    // From package
    PsiFile containingFile = member.getContainingFile();
    if (containingFile instanceof PsiClassOwner) {
      String packageName = ((PsiClassOwner)containingFile).getPackageName();
      PsiPackage aPackage = JavaPsiFacade.getInstance(member.getProject()).findPackage(packageName);
      if (aPackage != null) {
        NlsInfo info = fromAnnotationOwner(aPackage.getAnnotationList());
        if (info != Unspecified.UNKNOWN) {
          return info;
        }

        PsiAnnotation annotation = AnnotationUtil.findAnnotation(aPackage, ANNOTATION_NAMES, false);
        if (annotation != null) {
          return fromAnnotation(annotation);
        }
      }
    }
    return Unspecified.UNKNOWN;
  }
}
