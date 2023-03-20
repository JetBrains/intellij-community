// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;
import org.jetbrains.plugins.groovy.lang.resolve.api.CallParameter;
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature;
import org.jetbrains.plugins.groovy.lang.resolve.noncode.MixinMemberContributor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MultiProcessor;
import org.jetbrains.plugins.groovy.lang.typing.GroovyClosureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.unwrapClassType;

/**
 * @author Max Medvedev
 */
public final class GdkMethodUtil {

  public static final @NonNls Set<String> COLLECTION_METHOD_NAMES = Set.of(
    "each", "eachWithIndex", "any", "every", "reverseEach", "collect", "collectAll", "find", "findAll", "retainAll", "removeAll", "split",
    "groupBy", "groupEntriesBy", "findLastIndexOf", "findIndexValues", "findIndexOf"
  );
  @NlsSafe private static final String WITH = "with";
  @NlsSafe private static final String IDENTITY = "identity";
  @NlsSafe private static final String TAP = "tap";

  @NlsSafe public static final String EACH_WITH_INDEX = "eachWithIndex";
  @NlsSafe public static final String INJECT = "inject";
  @NlsSafe public static final String EACH_PERMUTATION = "eachPermutation";
  @NlsSafe public static final String WITH_DEFAULT = "withDefault";
  @NlsSafe public static final String SORT = "sort";
  @NlsSafe public static final String WITH_STREAM = "withStream";
  @NlsSafe public static final String WITH_STREAMS = "withStreams";
  @NlsSafe public static final String WITH_OBJECT_STREAMS = "withObjectStreams";

  private static final Map<String, String> AST_TO_EXPR_MAPPER =
    Map.of("org.codehaus.groovy.ast.expr.ClosureExpression", "groovy.lang.Closure",
           "org.codehaus.groovy.ast.expr.ListExpression", "java.util.List",
           "org.codehaus.groovy.ast.expr.MapExpression", "java.util.Map",
           "org.codehaus.groovy.ast.expr.TupleExpression", "groovy.lang.Tuple",
           "org.codehaus.groovy.ast.expr.MethodCallExpression", "java.lang.Object");

  private static final String MACRO_ORIGIN_INFO = "Macro method";


  private GdkMethodUtil() {
  }

  /**
   * @param place         - context of processing
   * @param processor     - processor to use
   * @param categoryClass - category class to process
   */
  public static boolean processCategoryMethods(final PsiElement place,
                                               final PsiScopeProcessor processor,
                                               @NotNull final ResolveState state,
                                               @NotNull final PsiClass categoryClass) {
    for (final PsiScopeProcessor each : MultiProcessor.allProcessors(processor)) {
      final PsiScopeProcessor delegate = new DelegatingScopeProcessor(each) {
        @Override
        public boolean execute(@NotNull PsiElement element, @NotNull ResolveState delegateState) {
          if (element instanceof PsiMethod method && isCategoryMethod((PsiMethod)element, null, null, null)) {
            return each.execute(GrGdkMethodImpl.createGdkMethod(method, false, generateOriginInfo(method)), delegateState);
          }
          return true;
        }
      };
      if (!categoryClass.processDeclarations(delegate, state, null, place)) return false;
    }
    return true;
  }

  /**
   * @param resolveContext is a qualifier of 'resolveContext.with {}'
   */
  public static boolean isInWithContext(PsiElement resolveContext) {
    if (resolveContext instanceof GrExpression) {
      final PsiElement parent = resolveContext.getParent();
      if (parent instanceof GrReferenceExpression && ((GrReferenceExpression)parent).getQualifier() == resolveContext) {
        final PsiElement pparent = parent.getParent();
        if (pparent instanceof GrMethodCall) {
          final PsiMethod method = ((GrMethodCall)pparent).resolveMethod();
          if (method instanceof GrGdkMethod && isWithName(method.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isWithName(String name) {
    return WITH.equals(name) || IDENTITY.equals(name) || TAP.equals(name);
  }

  @Nullable
  @NonNls
  public static String generateOriginInfo(PsiMethod method) {
    PsiClass cc = method.getContainingClass();
    if (cc == null) return null;
    //'\u2191'
    return "via " + cc.getName();
  }

  public static boolean processMixinToMetaclass(GrStatementOwner run,
                                                final PsiScopeProcessor processor,
                                                ResolveState state,
                                                PsiElement lastParent,
                                                PsiElement place) {
    if (!ResolveUtilKt.shouldProcessMethods(processor)) return true;
    GrStatement[] statements = run.getStatements();
    for (GrStatement statement : statements) {
      if (statement == lastParent) break;

      final MixinInfo result = getMixinTypes(statement);

      if (result != null) {
        final PsiClassType subjectType = result.subjectType;
        final GrReferenceExpression qualifier = result.ref;
        final PsiClass mixin = result.mixin;

        for (PsiScopeProcessor each : MultiProcessor.allProcessors(processor)) {
          if (!mixin.processDeclarations(new MixinMemberContributor.MixinProcessor(each, subjectType, qualifier), state, null, place)) {
            return false;
          }
        }
      }
      else {
        Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>> closureResult = getClosureMixins(statement);
        if (closureResult != null) {
          final PsiClassType subjectType = closureResult.first;
          final GrReferenceExpression qualifier = closureResult.second;
          final List<GrMethod> methods = closureResult.third;

          final DelegatingScopeProcessor delegate = new MixinMemberContributor.MixinProcessor(processor, subjectType, qualifier);
          for (GrMethod method : methods) {
            ResolveUtil.processElement(delegate, method, state);
          }
        }
      }
    }

    return true;
  }

  @NotNull
  private static GrMethod createMethod(@NotNull CallSignature<?> signature,
                                       @NotNull String name,
                                       @NotNull GrAssignmentExpression statement,
                                       @NotNull PsiClass closure) {
    final GrLightMethodBuilder builder = new GrLightMethodBuilder(statement.getManager(), name);

    int i = 0;
    for (CallParameter parameter : signature.getParameters()) {
      final String parameterName = ObjectUtils.notNull(parameter.getParameterName(), "p" + i);
      final PsiType type = ObjectUtils.notNull(parameter.getType(), () -> TypesUtil.getJavaLangObject(statement));
      builder.addParameter(parameterName, type, parameter.isOptional());
      i++;
    }

    builder.setNavigationElement(statement.getLValue());
    builder.setReturnType(signature.getReturnType());
    builder.setContainingClass(closure);
    return builder;
  }

  private static Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>> getClosureMixins(final GrStatement statement) {
    if (!(statement instanceof GrAssignmentExpression)) return null;
    return CachedValuesManager.getCachedValue(statement, () -> CachedValueProvider.Result.create(
      doGetClosureMixins((GrAssignmentExpression)statement),
      PsiModificationTracker.MODIFICATION_COUNT
    ));
  }

  @Nullable
  private static Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>> doGetClosureMixins(@NotNull GrAssignmentExpression assignment) {
    // Integer.class.metaClass.foo = {}
    final GrExpression lValue = assignment.getLValue(); // Integer.class.metaClass.foo
    if (!(lValue instanceof GrReferenceExpression)) {
      return null;
    }

    final String mixedMethodName = ((GrReferenceExpression)lValue).getReferenceName(); // foo
    if (mixedMethodName == null) {
      return null;
    }

    final GrExpression metaClassExpression = ((GrReferenceExpression)lValue).getQualifier(); // Integer.class.metaClass
    if (!(metaClassExpression instanceof GrReferenceExpression)) {
      return null;
    }

    final GrExpression rValue = assignment.getRValue(); // {}
    if (rValue == null) {
      return null;
    }

    final PsiElement resolved = ((GrReferenceExpression)metaClassExpression).resolve(); // getMetaClass
    if (!(resolved instanceof PsiMethod) || !isMetaClassMethod((PsiMethod)resolved)) {
      return null;
    }

    final GrExpression classQualifier = ((GrReferenceExpression)metaClassExpression).getQualifier(); // Integer.class
    final Pair<PsiClassType, GrReferenceExpression> original = getPsiClassFromReference(classQualifier);
    if (original == null) {
      return null;
    }

    final PsiType type = rValue.getType();
    if (!(type instanceof GroovyClosureType)) {
      return null;
    }

    final PsiClass closure = JavaPsiFacade.getInstance(assignment.getProject()).findClass(
      GroovyCommonClassNames.GROOVY_LANG_CLOSURE, assignment.getResolveScope()
    );
    if (closure == null) return null;

    final List<GrMethod> methods = new ArrayList<>();
    for (CallSignature<?> signature : ((GroovyClosureType)type).getSignatures()) {
      methods.add(createMethod(signature, mixedMethodName, assignment, closure));
    }
    return Trinity.create(original.first, original.second, methods);
  }

  /**
   * @param subjectType the type into which the methods are mixed in
   * @param ref         reference to subjectType
   * @param mixin       the type that is mixed into subjectType
   */
  record MixinInfo(PsiClassType subjectType, GrReferenceExpression ref, PsiClass mixin) {
  }

  @Nullable
  private static MixinInfo getMixinTypes(final GrStatement statement) {
    if (!(statement instanceof GrMethodCall)) return null;

    return CachedValuesManager.getCachedValue(statement, () -> {
      GrMethodCall call = (GrMethodCall)statement;

      Pair<PsiClassType, GrReferenceExpression> original = getTypeToMixIn(call);
      if (original == null) return CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

      PsiClass mix = getTypeToMix(call);
      if (mix == null) return CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

      return CachedValueProvider.Result
        .create(new MixinInfo(original.first, original.second, mix), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Nullable
  private static PsiClass getTypeToMix(GrMethodCall call) {
    if (!isSingleExpressionArg(call)) return null;

    GrExpression mixinRef = call.getExpressionArguments()[0];
    if (isClassRef(mixinRef)) {
      mixinRef = ((GrReferenceExpression)mixinRef).getQualifier();
    }

    if (mixinRef instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)mixinRef).resolve();
      if (resolved instanceof PsiClass) {
        return (PsiClass)resolved;
      }
    }

    return null;
  }

  private static boolean isSingleExpressionArg(@NotNull GrMethodCall call) {
    return call.getExpressionArguments().length == 1 &&
           !PsiImplUtil.hasNamedArguments(call.getArgumentList()) &&
           !call.hasClosureArguments();
  }

  @Nullable
  private static Pair<PsiClassType, GrReferenceExpression> getTypeToMixIn(GrMethodCall methodCall) {
    GrExpression invoked = methodCall.getInvokedExpression();
    if (!(invoked instanceof GrReferenceExpression referenceExpression)) return null;
    if (GrImportUtil.acceptName(referenceExpression, "mixin")) {

      PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiMethod && isMixinMethod((PsiMethod)resolved)) {
        GrExpression qualifier = referenceExpression.getQualifierExpression();
        Pair<PsiClassType, GrReferenceExpression> type = getPsiClassFromReference(qualifier);
        if (type != null) {
          return type;
        }
        if (qualifier != null && isMetaClass(qualifier.getType())) {
          if (qualifier instanceof GrMethodCall) qualifier = ((GrMethodCall)qualifier).getInvokedExpression();

          if (qualifier instanceof GrReferenceExpression) {
            GrExpression qqualifier = ((GrReferenceExpression)qualifier).getQualifierExpression();
            if (qqualifier != null) {
              Pair<PsiClassType, GrReferenceExpression> type1 = getPsiClassFromMetaClassReference(qqualifier);
              if (type1 != null) {
                return type1;
              }
            }
            else {
              PsiType qtype = PsiImplUtil.getQualifierType((GrReferenceExpression)qualifier);
              if (qtype instanceof PsiClassType && ((PsiClassType)qtype).resolve() != null) {
                return Pair.create((PsiClassType)qtype, (GrReferenceExpression)qualifier);
              }
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean isMixinMethod(@NotNull PsiMethod method) {
    if (method instanceof GrGdkMethod) method = ((GrGdkMethod)method).getStaticMethod();
    PsiClass containingClass = method.getContainingClass();
    String name = method.getName();
    return "mixin".equals(name) &&
           containingClass != null &&
           GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName());
  }

  private static boolean isMetaClassMethod(@NotNull PsiMethod method) {
    if (method instanceof GrGdkMethod) method = ((GrGdkMethod)method).getStaticMethod();
    PsiClass containingClass = method.getContainingClass();
    String name = method.getName();
    return "getMetaClass".equals(name) &&
           containingClass != null &&
           (method.getParameterList().isEmpty() ^
            GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName()));
  }

  private static boolean isMetaClass(PsiType qualifierType) {
    return qualifierType != null && qualifierType.equalsToText(GroovyCommonClassNames.GROOVY_LANG_META_CLASS);
  }

  private static boolean isClassRef(GrExpression mixinRef) {
    return mixinRef instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)mixinRef).getReferenceName());
  }

  /**
   * Integer.mixin(Foo)
   * Integer.class.mixin(Foo)
   */
  @Nullable
  private static Pair<PsiClassType, GrReferenceExpression> getPsiClassFromReference(@Nullable GrExpression ref) {
    if (ref == null) return null;

    final PsiType type = unwrapClassType(ref.getType());
    if (!(type instanceof PsiClassType)) return null;

    if (isClassRef(ref)) ref = ((GrReferenceExpression)ref).getQualifier();
    if (!(ref instanceof GrReferenceExpression)) return null;

    return Pair.create((PsiClassType)type, (GrReferenceExpression)ref);
  }

  /**
   * this.metaClass.mixin(Foo)
   */
  private static Pair<PsiClassType, GrReferenceExpression> getPsiClassFromMetaClassReference(@NotNull GrExpression expression) {
    final PsiType type = expression.getType();
    if (!(type instanceof PsiClassType)) return null;

    final GrReferenceExpression ref = expression instanceof GrReferenceExpression ? ((GrReferenceExpression)expression) : null;
    return Pair.create((PsiClassType)type, ref);
  }

  public static boolean isCategoryMethod(@NotNull PsiMethod method,
                                         @Nullable PsiType qualifierType,
                                         @Nullable PsiElement place,
                                         @Nullable PsiSubstitutor substitutor) {
    if (!method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false;

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0) return false;

    if (qualifierType == null) return true;

    PsiType selfType = parameters[0].getType();
    if (selfType instanceof PsiPrimitiveType) return false;

    if (substitutor != null) {
      selfType = substitutor.substitute(selfType);
    }

    if (PsiTypesUtil.classNameEquals(selfType, CommonClassNames.JAVA_LANG_CLASS) &&
        place instanceof GrReferenceExpression &&
        ((GrReferenceExpression)place).resolve() instanceof PsiClass) {   // ClassType.categoryMethod()  where categoryMethod(Class<> cl, ...)
      return TypesUtil.isAssignableByMethodCallConversion(selfType, TypesUtil.createJavaLangClassType(qualifierType, method), method);
    }
    return TypesUtil.isAssignableByMethodCallConversion(selfType, qualifierType, method);
  }

  @Nullable
  public static PsiClassType getCategoryType(@NotNull final PsiClass categoryAnnotationOwner) {
    return CachedValuesManager.getCachedValue(categoryAnnotationOwner, new CachedValueProvider<>() {
      @Override
      public Result<PsiClassType> compute() {
        return Result.create(inferCategoryType(categoryAnnotationOwner), PsiModificationTracker.MODIFICATION_COUNT);
      }

      @Nullable
      private PsiClassType inferCategoryType(final PsiClass aClass) {
        return RecursionManager.doPreventingRecursion(aClass, true, (NullableComputable<PsiClassType>)() -> {
          final PsiModifierList modifierList = aClass.getModifierList();
          if (modifierList == null) return null;

          final PsiAnnotation annotation = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_CATEGORY);
          if (annotation == null) return null;

          PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
          if (!(value instanceof GrReferenceExpression)) return null;

          if ("class".equals(((GrReferenceExpression)value).getReferenceName())) value = ((GrReferenceExpression)value).getQualifier();
          if (!(value instanceof GrReferenceExpression)) return null;

          final PsiElement resolved = ((GrReferenceExpression)value).resolve();
          if (!(resolved instanceof PsiClass)) return null;

          String className = ((PsiClass)resolved).getQualifiedName();
          if (className == null) className = ((PsiClass)resolved).getName();
          if (className == null) return null;

          return JavaPsiFacade.getElementFactory(aClass.getProject()).createTypeByFQClassName(className, resolved.getResolveScope());
        });
      }
    });
  }

  public static boolean isWithOrIdentity(@Nullable PsiElement element) {
    if (element instanceof PsiMethod && isWithName(((PsiMethod)element).getName())) {
      if (element instanceof GrGdkMethod) {
        element = ((GrGdkMethod)element).getStaticMethod();
      }
      final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
      if (containingClass != null) {
        if (GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName())) {
          return true;
        }
      }
    }
    return false;
  }

  public static PsiMethod createMacroMethod(@NotNull PsiMethod prototype) {
    GrLightMethodBuilder syntheticMacro = new GrLightMethodBuilder(prototype.getManager(), prototype.getName());
    syntheticMacro.setModifiers(new String[]{PsiModifier.STATIC});
    PsiParameterList list = prototype.getParameterList();
    for (int i = 1; i < list.getParametersCount(); ++i) {
      PsiParameter actualParameter = list.getParameter(i);
      String generatedParameterType;
      String name;
      if (actualParameter == null) {
        generatedParameterType = CommonClassNames.JAVA_LANG_OBJECT;
        name = "expression";
      }
      else {
        PsiType type = actualParameter.getType();
        if (!type.equals(PsiTypes.nullType())) {
          generatedParameterType = AST_TO_EXPR_MAPPER.getOrDefault(type.getCanonicalText(), CommonClassNames.JAVA_LANG_OBJECT);
        }
        else {
          generatedParameterType = CommonClassNames.JAVA_LANG_OBJECT;
        }
        name = actualParameter.getName();
      }
      syntheticMacro.addParameter(
        new GrLightParameter(name,
                             PsiType.getTypeByName(generatedParameterType, prototype.getProject(), GlobalSearchScope.allScope(prototype.getProject())),
                             prototype));
    }
    syntheticMacro.setNavigationElement(prototype);
    syntheticMacro.setOriginInfo(MACRO_ORIGIN_INFO);
    return syntheticMacro;
  }

  public static boolean isMacro(@Nullable PsiMethod method) {
    return method instanceof OriginInfoAwareElement && MACRO_ORIGIN_INFO.equals(((OriginInfoAwareElement)method).getOriginInfo());
  }
}
