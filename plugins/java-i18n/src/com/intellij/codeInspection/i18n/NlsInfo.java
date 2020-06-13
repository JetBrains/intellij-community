// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.NlsContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import kotlin.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nls.Capitalization;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.util.UastExpressionUtils;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Contains information about localization status.
 * The class has three implementations {@link Localized}, {@link NonLocalized} and {@link Unspecified}, 
 * which may provide additional information.
 */
public abstract class NlsInfo {
  private static final @NotNull Set<String> ANNOTATION_NAMES = ContainerUtil.immutableSet(AnnotationUtil.NLS, AnnotationUtil.NON_NLS);
  private static final @NotNull String NLS_CONTEXT = "com.intellij.openapi.util.NlsContext";

  /**
   * Describes a string that should be localized
   */
  public static class Localized extends NlsInfo {
    private static final Localized NLS = new Localized(Capitalization.NotSpecified, "", "");
    private static final Localized NLS_TITLE = new Localized(Capitalization.Title, "", "");
    private static final Localized NLS_SENTENCE = new Localized(Capitalization.Sentence, "", "");
    private final @NotNull Capitalization myCapitalization;
    private final @NotNull @NonNls String myPrefix;
    private final @NotNull @NonNls String mySuffix;

    private Localized(@NotNull Capitalization capitalization,
                      @NotNull @NonNls String prefix,
                      @NotNull @NonNls String suffix) {
      super(ThreeState.YES);
      myCapitalization = capitalization;
      myPrefix = prefix;
      mySuffix = suffix;
    }

    /**
     * @return expected string capitalization
     * @see Nls#capitalization() 
     */
    public @NotNull Capitalization getCapitalization() {
      return myCapitalization;
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

    private @NotNull NlsInfo withPrefixAndSuffix(@NotNull String prefix, @NotNull String suffix) {
      if (prefix.equals(myPrefix) && suffix.equals(mySuffix)) {
        return this;
      }
      return new Localized(myCapitalization, prefix, suffix);
    }
  }

  /**
   * Describes a string that should not be localized
   */
  public static class NonLocalized extends NlsInfo {
    private static final NonLocalized INSTANCE = new NonLocalized();
    
    private NonLocalized() {super(ThreeState.NO);}
  }

  /**
   * Describes a string, whose localization status is not explicitly specified. 
   * Whether the string should be localized or not may depend on the user settings and various heuristics. 
   */
  public static class Unspecified extends NlsInfo {
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
    NlsInfo info = fromMethodReturn(expression);
    if (info != Unspecified.UNKNOWN) return info;
    info = fromInitializer(expression);
    if (info != Unspecified.UNKNOWN) return info;
    return fromArgument(expression);
  }

  public static @NotNull NlsInfo forModifierListOwner(@NotNull PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter) {
      PsiElement scope = ((PsiParameter)owner).getDeclarationScope();
      if (scope instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)scope;
        PsiParameterList list = method.getParameterList();
        int index = list.getParameterIndex((PsiParameter)owner);
        if (index < 0) {
          return Unspecified.UNKNOWN;
        }
        return fromMethodParameter(method, index, null);
      }
    }
    if (owner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)owner;
      return fromMethodReturn(method, method.getReturnType(), null);
    }
    return fromAnnotationOwner(owner.getModifierList());
  }

  public static @NotNull Capitalization getCapitalization(@NotNull PsiModifierListOwner owner) {
    NlsInfo info = forModifierListOwner(owner);
    if (info instanceof Localized) {
      return ((Localized)info).getCapitalization();
    }
    return Capitalization.NotSpecified;
  }

  private static NlsInfo fromInitializer(UExpression expression) {
    UElement parent;
    PsiElement var = null;
    while (true) {
      parent = expression.getUastParent();
      if (!(parent instanceof UParenthesizedExpression || parent instanceof UIfExpression ||
            (parent instanceof UPolyadicExpression && ((UPolyadicExpression)parent).getOperator() == UastBinaryOperator.PLUS))) {
        break;
      }
      expression = (UExpression)parent;
    }
    if (parent instanceof UBinaryExpression) {
      UBinaryExpression binOp = (UBinaryExpression)parent;
      UastBinaryOperator operator = binOp.getOperator();
      if ((operator == UastBinaryOperator.ASSIGN || operator == UastBinaryOperator.PLUS_ASSIGN) &&
          expression.equals(binOp.getRightOperand())) {
        UReferenceExpression lValue = ObjectUtils.tryCast(UastUtils.skipParenthesizedExprDown(binOp.getLeftOperand()), 
                                                          UReferenceExpression.class);
        if (lValue != null) {
          var = lValue.resolve();
        }
      }
    }
    else if (parent instanceof UVariable) {
      var = parent.getJavaPsi();
    }
    if (var instanceof PsiVariable) {
      NlsInfo info = fromAnnotationOwner(((PsiVariable)var).getModifierList());
      if (info != Unspecified.UNKNOWN) return info;
      return fromType(((PsiVariable)var).getType());
    }
    return Unspecified.UNKNOWN;
  }

  private static @NotNull NlsInfo fromArgument(@NotNull UExpression expression) {
    UElement parent = UastUtils.skipParenthesizedExprUp(expression.getUastParent());
    if (parent instanceof UPolyadicExpression) {
      parent = UastUtils.skipParenthesizedExprUp(parent.getUastParent());
    }
    UCallExpression callExpression = UastUtils.getUCallExpression(parent);
    if (callExpression == null) return Unspecified.UNKNOWN;

    List<UExpression> arguments = callExpression.getValueArguments();
    OptionalInt idx = IntStream.range(0, arguments.size())
      .filter(i -> UastUtils.isUastChildOf(expression, arguments.get(i), false))
      .findFirst();

    if (!idx.isPresent()) return Unspecified.UNKNOWN;

    PsiMethod method = callExpression.resolve();
    if (method == null) return Unspecified.UNKNOWN;
    NlsInfo fromParameter = fromMethodParameter(method, idx.getAsInt(), null);
    if (fromParameter != Unspecified.UNKNOWN) {
      return fromParameter;
    }
    PsiParameter parameter = method.getParameterList().getParameter(idx.getAsInt());
    if (parameter != null) {
      PsiType parameterType = parameter.getType();
      PsiElement psi = callExpression.getSourcePsi();
      if (psi instanceof PsiMethodCallExpression) {
        PsiSubstitutor substitutor = ((PsiMethodCallExpression)psi).getMethodExpression().advancedResolve(false).getSubstitutor();
        parameterType = substitutor.substitute(parameterType);
      }
      NlsInfo info = fromType(parameterType);
      if (info != Unspecified.UNKNOWN) {
        return info;
      }
    }
    return new Unspecified(parameter);
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

  private static @NotNull NlsInfo fromMethodReturn(@NotNull UExpression expression) {
    PsiMethod method;
    PsiType returnType = null;
    UNamedExpression nameValuePair = UastUtils.getParentOfType(expression, UNamedExpression.class);
    if (nameValuePair != null) {
      method = UastUtils.getAnnotationMethod(nameValuePair);
    }
    else {
      UElement parent = UastUtils.skipParenthesizedExprUp(expression.getUastParent());
      while (parent instanceof UCallExpression &&
             (UastExpressionUtils.isArrayInitializer(parent) || UastExpressionUtils.isNewArrayWithInitializer(parent))) {
        parent = UastUtils.skipParenthesizedExprUp(parent.getUastParent());
      }
      if (parent == null) return Unspecified.UNKNOWN;
      final UReturnExpression returnStmt =
        UastUtils.getParentOfType(parent, UReturnExpression.class, false, UCallExpression.class, ULambdaExpression.class);
      if (returnStmt == null) {
        return Unspecified.UNKNOWN;
      }
      UElement jumpTarget = returnStmt.getJumpTarget();
      if (jumpTarget instanceof UMethod) {
        method = ((UMethod)jumpTarget).getJavaPsi();
      }
      else if (jumpTarget instanceof ULambdaExpression) {
        PsiType type = ((ULambdaExpression)jumpTarget).getFunctionalInterfaceType();
        returnType = LambdaUtil.getFunctionalInterfaceReturnType(type);
        if (type == null) return Unspecified.UNKNOWN;
        method = LambdaUtil.getFunctionalInterfaceMethod(type);
      }
      else {
        return Unspecified.UNKNOWN;
      }
    }
    if (method == null) return Unspecified.UNKNOWN;

    return fromMethodReturn(method, returnType, null);
  }

  private static @NotNull NlsInfo fromMethodReturn(@NotNull PsiMethod method,
                                                   @Nullable PsiType returnType,
                                                   @Nullable Collection<? super PsiMethod> processed) {
    if (processed != null && processed.contains(method)) {
      return Unspecified.UNKNOWN;
    }
    NlsInfo methodInfo = fromAnnotationOwner(method.getModifierList());
    if (methodInfo != Unspecified.UNKNOWN) {
      return methodInfo;
    }

    if (returnType != null) {
      NlsInfo info = fromType(returnType);
      if (info != Unspecified.UNKNOWN) {
        return info;
      }
    }
    final PsiMethod[] superMethods = method.findSuperMethods();
    if (superMethods.length > 0) {
      if (processed == null) {
        processed = new THashSet<>();
      }
      processed.add(method);
      for (PsiMethod superMethod : superMethods) {
        NlsInfo superInfo = fromMethodReturn(superMethod, null, processed);
        if (superInfo != Unspecified.UNKNOWN) return superInfo;
      }
    }
    return new Unspecified(method);
  }


  private static @NotNull NlsInfo fromAnnotationOwner(@Nullable PsiAnnotationOwner owner) {
    if (owner == null) return Unspecified.UNKNOWN;
    if (owner instanceof PsiModifierList) {
      return CachedValuesManager.getCachedValue((PsiModifierList)owner, () ->
        CachedValueProvider.Result.create(computeFromAnnotationOwner(owner), PsiModificationTracker.MODIFICATION_COUNT));
    }
    return computeFromAnnotationOwner(owner);
  }

  @NotNull
  private static NlsInfo computeFromAnnotationOwner(@NotNull PsiAnnotationOwner owner) {
    for (PsiAnnotation annotation : owner.getAnnotations()) {
      NlsInfo info = fromAnnotation(annotation);
      if (info != Unspecified.UNKNOWN) {
        return info;
      }
      info = fromMetaAnnotation(annotation);
      if (info != Unspecified.UNKNOWN) {
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
    return Unspecified.UNKNOWN;
  }

  private static @NotNull NlsInfo fromMetaAnnotation(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement element = annotation.getNameReferenceElement();
    if (element == null) return Unspecified.UNKNOWN;
    PsiClass annotationClass = ObjectUtils.tryCast(element.resolve(), PsiClass.class);
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
      return ((Localized)baseInfo).withPrefixAndSuffix(prefix, suffix);
    }
    return baseInfo;
  }

  private static @NotNull NlsInfo fromAnnotation(@NotNull PsiAnnotation annotation) {
    if (annotation.hasQualifiedName(AnnotationUtil.NON_NLS) ||
        annotation.hasQualifiedName(AnnotationUtil.PROPERTY_KEY)) {
      return NonLocalized.INSTANCE;
    }
    if (annotation.hasQualifiedName(AnnotationUtil.NLS)) {
      PsiAnnotationMemberValue value = annotation.findAttributeValue("capitalization");
      String name = null;
      if (value instanceof PsiReferenceExpression) {
        // Java plugin returns reference for enum constant in annotation value
        name = ((PsiReferenceExpression)value).getReferenceName();
      }
      else if (value instanceof PsiLiteralExpression) {
        // But Kotlin plugin returns kotlin.Pair (enumClass : ClassId, constantName : Name) for enum constant in annotation value!
        Pair<?, ?> pair = ObjectUtils.tryCast(((PsiLiteralExpression)value).getValue(), Pair.class);
        if (pair != null && pair.getSecond() != null) {
          name = pair.getSecond().toString();
        }
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

  private static @NotNull NlsInfo fromMethodParameter(@NotNull PsiMethod method,
                                                      int idx,
                                                      @Nullable Collection<? super PsiMethod> processed) {
    if (processed != null && processed.contains(method)) {
      return Unspecified.UNKNOWN;
    }

    final PsiParameter[] params = method.getParameterList().getParameters();
    PsiParameter param;
    if (idx >= params.length) {
      PsiParameter lastParam = ArrayUtil.getLastElement(params);
      if (lastParam == null || !lastParam.isVarArgs()) return Unspecified.UNKNOWN;
      param = lastParam;
    }
    else {
      param = params[idx];
    }
    NlsInfo explicit = fromAnnotationOwner(param.getModifierList());
    if (explicit != Unspecified.UNKNOWN) {
      return explicit;
    }

    final PsiMethod[] superMethods = method.findSuperMethods();
    if (superMethods.length > 0) {
      if (processed == null) {
        processed = new THashSet<>();
      }
      processed.add(method);
      for (PsiMethod superMethod : superMethods) {
        NlsInfo superInfo = fromMethodParameter(superMethod, idx, processed);
        if (superInfo != Unspecified.UNKNOWN) return superInfo;
      }
    }
    return fromContainer(method);
  }

  private static @NotNull NlsInfo fromContainer(@NotNull PsiMethod method) {
    // From class
    PsiClass containingClass = method.getContainingClass();
    while (containingClass != null) {
      NlsInfo classInfo = fromAnnotationOwner(containingClass.getModifierList());
      if (classInfo != Unspecified.UNKNOWN) {
        return classInfo;
      }
      containingClass = containingClass.getContainingClass();
    }

    // From package
    PsiFile containingFile = method.getContainingFile();
    if (containingFile instanceof PsiClassOwner) {
      String packageName = ((PsiClassOwner)containingFile).getPackageName();
      PsiPackage aPackage = JavaPsiFacade.getInstance(method.getProject()).findPackage(packageName);
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
