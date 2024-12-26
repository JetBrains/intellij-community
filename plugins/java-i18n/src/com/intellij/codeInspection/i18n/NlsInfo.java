// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import com.intellij.codeInspection.restriction.RestrictionInfoFactory;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.openapi.util.NlsContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nls.Capitalization;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Contains information about localization status.
 * The class has three implementations {@link Localized}, {@link NonLocalized} and {@link NlsUnspecified},
 * which may provide additional information.
 */
public abstract class NlsInfo implements RestrictionInfo {
  static final String NLS_SAFE = "com.intellij.openapi.util.NlsSafe";
  private static final @NotNull String NLS_CONTEXT = "com.intellij.openapi.util.NlsContext";
  private static final @NotNull Set<String> ANNOTATION_NAMES = Set.of(AnnotationUtil.NLS, AnnotationUtil.NON_NLS, NLS_SAFE, AnnotationUtil.PROPERTY_KEY);
  private final @NotNull ThreeState myNls;

  private NlsInfo(@NotNull ThreeState nls) {
    myNls = nls;
  }
  
  public static NlsInfoFactory factory() {
    return NlsInfoFactory.INSTANCE;
  }
  

  /**
   * @return {@link ThreeState#YES} if the string must be localized (see {@link Localized});<br>
   * {@link ThreeState#NO} if the string must not be localized (see {@link NonLocalized});<br>
   * {@link ThreeState#UNSURE} if it's not explicitly specified (see {@link NlsUnspecified});
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
   * @param expression               expression to determine the localization status for
   * @param allowStringModifications whether string modifications are allowed
   * @return localization status
   */
  static @NotNull NlsInfo forExpression(@NotNull UExpression expression, boolean allowStringModifications) {
    expression = StringFlowUtil.goUp(expression, allowStringModifications, NlsInfoFactory.INSTANCE);
    NlsInfo target = fromEqualityCheck(expression, allowStringModifications);
    if (target != NlsUnspecified.UNKNOWN) return target;
    AnnotationContext context = AnnotationContext.fromExpression(expression);
    return fromAnnotationContext(expression.getUastParent(), context);
  }

  private static @NotNull NlsInfo fromEqualityCheck(@NotNull UExpression expression, boolean allowStringModifications) {
    UElement parent = UastUtils.skipParenthesizedExprUp(expression.getUastParent());
    if (parent instanceof UCallExpression) {
      String name = ((UCallExpression)parent).getMethodName();
      if (name != null && (name.equals("equals") ||
                           (allowStringModifications && name.equals("startsWith") || name.equals("endsWith") ||
                            name.equals("equalsIgnoreCase") || name.equals("contains")))) {
        var qualifiedCall = ObjectUtils.tryCast(parent.getUastParent(), UQualifiedReferenceExpression.class);
        if (qualifiedCall != null && parent.equals(qualifiedCall.getSelector())) {
          return fromVariableReference(qualifiedCall.getReceiver());
        }
      }
    }
    if (parent instanceof UBinaryExpression) {
      UastBinaryOperator operator = ((UBinaryExpression)parent).getOperator();
      if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
        UExpression left = ((UBinaryExpression)parent).getLeftOperand();
        UExpression right = ((UBinaryExpression)parent).getRightOperand();
        if (AnnotationContext.expressionsAreEquivalent(left, expression)) {
          return fromVariableReference(right);
        }
        if (AnnotationContext.expressionsAreEquivalent(right, expression)) {
          return fromVariableReference(left);
        }
      }
    }
    return NlsUnspecified.UNKNOWN;
  }

  private static @NotNull NlsInfo fromVariableReference(UExpression receiver) {
    if (receiver instanceof UReferenceExpression && TypeUtils.isJavaLangString(receiver.getExpressionType())) {
      PsiElement target = ((UReferenceExpression)receiver).resolve();
      if (target instanceof PsiVariable) {
        return factory().fromModifierListOwner((PsiVariable)target);
      }
    }
    return NlsUnspecified.UNKNOWN;
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
  
  private static class NlsInfoFactory implements RestrictionInfoFactory<NlsInfo> {

    private static final NlsInfoFactory INSTANCE = new NlsInfoFactory();

    @Override
    public @NotNull NlsInfo fromAnnotationOwner(@Nullable PsiAnnotationOwner annotationOwner) {
      return NlsInfo.fromAnnotationOwner(annotationOwner);
    }

    @Override
    public @NotNull NlsInfo fromModifierListOwner(@NotNull PsiModifierListOwner modifierListOwner) {
      return fromAnnotationContext(null, AnnotationContext.fromModifierListOwner(modifierListOwner));
    }
  }

  private static @NotNull NlsInfo fromAnnotationContext(UElement parent, AnnotationContext context) {
    NlsInfo info = fromType(context.getType());
    if (info != NlsUnspecified.UNKNOWN) return info;
    PsiModifierListOwner owner = context.getOwner();
    if (owner == null) return NlsUnspecified.UNKNOWN;
    info = fromAnnotationOwner(owner.getModifierList());
    if (info != NlsUnspecified.UNKNOWN) return info;
    if (owner instanceof PsiParameter parameter) {
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
    if (info.getKind() != RestrictionInfoKind.KNOWN) {
      info = context.secondaryItems().map(item -> fromAnnotationOwner(item.getModifierList()))
        .filter(inf -> inf != NlsUnspecified.UNKNOWN).findFirst().orElse(info);
    }
    if (info == NlsUnspecified.UNKNOWN) {
      PsiMember member =
        ObjectUtils.tryCast(owner instanceof PsiParameter ? ((PsiParameter)owner).getDeclarationScope() : owner, PsiMember.class);
      if (member != null) {
        info = fromContainer(member);
      }
    }
    if (info == NlsUnspecified.UNKNOWN && (!(parent instanceof UCallExpression) || owner instanceof PsiParameter)) {
      info = new NlsUnspecified(owner);
    }
    return info;
  }

  private static @NotNull NlsInfo fromType(PsiType type) {
    if (type == null) return NlsUnspecified.UNKNOWN;
    Ref<NlsInfo> result = Ref.create(NlsUnspecified.UNKNOWN);
    InheritanceUtil.processSuperTypes(type, true, eachType -> {
      NlsInfo info = fromAnnotationOwner(eachType);
      if (info != NlsUnspecified.UNKNOWN) {
        result.set(info);
        return false;
      }
      return !(eachType instanceof PsiClassType) || PsiUtil.resolveClassInClassTypeOnly(eachType) instanceof PsiTypeParameter;
    });
    return result.get();
  }

  static @NotNull NlsInfo fromUVariable(@NotNull UVariable owner) {
    for (UAnnotation annotation : owner.getUAnnotations()) {
      NlsInfo info = fromAnnotation(annotation);
      if (info != NlsUnspecified.UNKNOWN) {
        return info;
      }
      info = fromMetaAnnotation(annotation.resolve());
      if (info != NlsUnspecified.UNKNOWN) {
        return info;
      }
    }
    return NlsUnspecified.UNKNOWN;
  }

  private static @NotNull NlsInfo fromAnnotationOwner(@Nullable PsiAnnotationOwner owner) {
    if (owner == null) return NlsUnspecified.UNKNOWN;
    if (owner instanceof PsiModifierList) {
      return CachedValuesManager.getCachedValue((PsiModifierList)owner, () ->
        CachedValueProvider.Result.create(computeFromAnnotationOwner(owner), PsiModificationTracker.MODIFICATION_COUNT));
    }
    return computeFromAnnotationOwner(owner);
  }

  private static @NotNull NlsInfo computeFromAnnotationOwner(@NotNull PsiAnnotationOwner owner) {
    for (PsiAnnotation annotation : owner.getAnnotations()) {
      NlsInfo info = fromAnnotation(annotation);
      if (info != NlsUnspecified.UNKNOWN) {
        return info;
      }
      UAnnotation uAnnotation = UastContextKt.toUElement(annotation, UAnnotation.class);
      if (uAnnotation != null) {
        info = fromMetaAnnotation(uAnnotation.resolve());
      } else {
        String name = annotation.getQualifiedName();
        if (name != null) {
          info = fromMetaAnnotation(JavaPsiFacade.getInstance(annotation.getProject()).findClass(name, annotation.getResolveScope()));
        }
      }
      if (info != NlsUnspecified.UNKNOWN) {
        return info;
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
    return NlsUnspecified.UNKNOWN;
  }

  private static @NotNull NlsInfo fromMetaAnnotation(@Nullable PsiClass annotationClass) {
    if (annotationClass == null) return NlsUnspecified.UNKNOWN;
    NlsInfo baseInfo = NlsUnspecified.UNKNOWN;
    String prefix = "";
    String suffix = "";
    for (PsiAnnotation metaAnno : annotationClass.getAnnotations()) {
      if (metaAnno.hasQualifiedName(NLS_CONTEXT)) {
        prefix = StringUtil.notNullize(AnnotationUtil.getStringAttributeValue(metaAnno, "prefix"));
        suffix = StringUtil.notNullize(AnnotationUtil.getStringAttributeValue(metaAnno, "suffix"));
      }
      else {
        NlsInfo info = fromAnnotation(metaAnno);
        if (info != NlsUnspecified.UNKNOWN) {
          baseInfo = info;
        }
      }
    }
    if (baseInfo instanceof Localized) {
      return ((Localized)baseInfo).withPrefixAndSuffix(prefix, suffix).withAnnotation(annotationClass.getQualifiedName());
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

  private static @NotNull NlsInfo fromAnnotationInfo(String qualifiedName, Supplier<? extends UExpression> capitalization) {
    if (qualifiedName == null) return NlsUnspecified.UNKNOWN;
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
    return NlsUnspecified.UNKNOWN;
  }

  private static @NotNull NlsInfo fromContainer(@NotNull PsiMember member) {
    // From class
    PsiClass containingClass = member.getContainingClass();
    while (containingClass != null) {
      NlsInfo classInfo = fromAnnotationOwner(containingClass.getModifierList());
      if (classInfo != NlsUnspecified.UNKNOWN) {
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
        if (info != NlsUnspecified.UNKNOWN) {
          return info;
        }

        PsiAnnotation annotation = AnnotationUtil.findAnnotation(aPackage, ANNOTATION_NAMES, false);
        if (annotation != null) {
          return fromAnnotation(annotation);
        }
      }
    }
    return NlsUnspecified.UNKNOWN;
  }

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

    private @NotNull Localized withAnnotation(@Nullable String qualifiedName) {
      if (Objects.equals(qualifiedName, myAnnotationName)) {
        return this;
      }
      return new Localized(myCapitalization, myPrefix, mySuffix, qualifiedName);
    }

    @Override
    public @NotNull RestrictionInfoKind getKind() {
      return RestrictionInfoKind.KNOWN;
    }
  }

  /**
   * Describes a string that should not be localized, but it's still safe to be displayed in UI
   * (e.g. file name).
   */
  public static final class NlsSafe extends NlsInfo {
    private static final NlsSafe INSTANCE = new NlsSafe();

    private NlsSafe() { super(ThreeState.NO); }

    @Override
    public @NotNull RestrictionInfoKind getKind() {
      return RestrictionInfoKind.KNOWN;
    }
  }

  /**
   * Describes a string that should not be localized
   */
  public static final class NonLocalized extends NlsInfo {
    private static final NonLocalized INSTANCE = new NonLocalized();

    private NonLocalized() { super(ThreeState.NO); }

    @Override
    public @NotNull RestrictionInfoKind getKind() {
      return RestrictionInfoKind.KNOWN;
    }
  }

  /**
   * Describes a string, whose localization status is not explicitly specified.
   * Whether the string should be localized or not may depend on the user settings and various heuristics.
   */
  public static final class NlsUnspecified extends NlsInfo {
    private static final NlsUnspecified UNKNOWN = new NlsUnspecified(null);

    private final @Nullable PsiModifierListOwner myCandidate;

    private NlsUnspecified(@Nullable PsiModifierListOwner candidate) {
      super(ThreeState.UNSURE);
      myCandidate = candidate;
    }

    /**
     * @return a place where it's desired to put an explicit {@link Nls} or {@link NonNls} annotation.
     * May return null if such kind of place cannot be determined.
     */
    public @Nullable PsiModifierListOwner getAnnotationCandidate() {
      return myCandidate;
    }

    @Override
    public @NotNull RestrictionInfoKind getKind() {
      return this == UNKNOWN ? RestrictionInfoKind.UNKNOWN : RestrictionInfoKind.UNSPECIFIED;
    }
  }
}
