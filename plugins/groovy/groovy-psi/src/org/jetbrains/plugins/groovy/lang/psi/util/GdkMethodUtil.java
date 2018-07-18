// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.noncode.MixinMemberContributor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrDelegatingScopeProcessorWithHints;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor;

import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.unwrapClassType;

/**
 * @author Max Medvedev
 */
public class GdkMethodUtil {

  public static final Set<String> COLLECTION_METHOD_NAMES = ContainerUtil.newHashSet(
    "each", "eachWithIndex", "any", "every", "reverseEach", "collect", "collectAll", "find", "findAll", "retainAll", "removeAll", "split",
    "groupBy", "groupEntriesBy", "findLastIndexOf", "findIndexValues", "findIndexOf"
  );
  @NonNls private static final String WITH = "with";
  @NonNls private static final String IDENTITY = "identity";

  @NonNls public static final String EACH_WITH_INDEX = "eachWithIndex";
  @NonNls public static final String INJECT = "inject";
  @NonNls public static final String EACH_PERMUTATION = "eachPermutation";
  @NonNls public static final String WITH_DEFAULT = "withDefault";
  @NonNls public static final String SORT = "sort";
  @NonNls public static final String WITH_STREAM = "withStream";
  @NonNls public static final String WITH_STREAMS = "withStreams";
  @NonNls public static final String WITH_OBJECT_STREAMS = "withObjectStreams";

  private GdkMethodUtil() {
  }

  /**
   *
   * @param place - context of processing
   * @param processor - processor to use
   * @param categoryClass - category class to process
   * @return
   */
  public static boolean processCategoryMethods(final PsiElement place,
                                               final PsiScopeProcessor processor,
                                               @NotNull final ResolveState state,
                                               @NotNull final PsiClass categoryClass) {
    for (final PsiScopeProcessor each : GroovyResolverProcessor.allProcessors(processor)) {
      final PsiScopeProcessor delegate = new GrDelegatingScopeProcessorWithHints(each, null, ClassHint.RESOLVE_KINDS_METHOD) {
        @Override
        public boolean execute(@NotNull PsiElement element, @NotNull ResolveState delegateState) {
          if (element instanceof PsiMethod && isCategoryMethod((PsiMethod)element, null, null, null)) {
            PsiMethod method = (PsiMethod)element;
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
    return WITH.equals(name) || IDENTITY.equals(name);
  }

  @Nullable
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
    GrStatement[] statements = run.getStatements();
    for (GrStatement statement : statements) {
      if (statement == lastParent) break;

      final Trinity<PsiClassType, GrReferenceExpression, PsiClass> result = getMixinTypes(statement);

      if (result != null) {
        final PsiClassType subjectType = result.first;
        final GrReferenceExpression qualifier = result.second;
        final PsiClass mixin = result.third;

        for (PsiScopeProcessor each : GroovyResolverProcessor.allProcessors(processor)) {
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
  private static GrMethod createMethod(@NotNull GrClosureSignature signature,
                                       @NotNull String name,
                                       @NotNull GrAssignmentExpression statement,
                                       @NotNull PsiClass closure) {
    final GrLightMethodBuilder builder = new GrLightMethodBuilder(statement.getManager(), name);

    GrClosureParameter[] parameters = signature.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrClosureParameter parameter = parameters[i];
      final String parameterName = parameter.getName() != null ? parameter.getName() : "p" + i;
      final PsiType type = parameter.getType() != null ? parameter.getType() : TypesUtil.getJavaLangObject(statement);
      builder.addParameter(parameterName, type, parameter.isOptional());
    }

    builder.setNavigationElement(statement.getLValue());
    builder.setReturnType(signature.getReturnType());
    builder.setContainingClass(closure);
    return builder;
  }

  private static Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>> getClosureMixins(final GrStatement statement) {
    if (!(statement instanceof GrAssignmentExpression)) return null;

    final GrAssignmentExpression assignment = (GrAssignmentExpression)statement;
    return CachedValuesManager.getCachedValue(statement, new CachedValueProvider<Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>>>() {
      @Nullable
      @Override
      public Result<Trinity<PsiClassType, GrReferenceExpression, List<GrMethod>>> compute() {

        Pair<PsiClassType, GrReferenceExpression> original = getTypeToMixIn(assignment);
        if (original == null) return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

        final Pair<GrSignature, String> signatures = getTypeToMix(assignment);
        if (signatures == null) return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

        final String name = signatures.second;

        final List<GrMethod> methods = ContainerUtil.newArrayList();
        final PsiClass closure = JavaPsiFacade.getInstance(statement.getProject()).findClass(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, statement.getResolveScope());
        if (closure == null) return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

        signatures.first.accept(new GrSignatureVisitor() {
          @Override
          public void visitClosureSignature(GrClosureSignature signature) {
            super.visitClosureSignature(signature);
            GrMethod method = createMethod(signature, name, assignment, closure);
            methods.add(method);
          }
        });

        return Result.create(Trinity.create(original.first, original.second, methods), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  private static Pair<PsiClassType, GrReferenceExpression> getTypeToMixIn(GrAssignmentExpression assignment) {
    final GrExpression lvalue = assignment.getLValue();
    if (lvalue instanceof GrReferenceExpression) {
      final GrExpression metaClassRef = ((GrReferenceExpression)lvalue).getQualifier();
      if (metaClassRef instanceof GrReferenceExpression &&
          (GrImportUtil.acceptName((GrReferenceElement)metaClassRef, "metaClass") ||
           GrImportUtil.acceptName((GrReferenceElement)metaClassRef, "getMetaClass"))) {
        final PsiElement resolved = ((GrReferenceElement)metaClassRef).resolve();
        if (resolved instanceof PsiMethod && isMetaClassMethod((PsiMethod)resolved)) {
          return getPsiClassFromReference(((GrReferenceExpression)metaClassRef).getQualifier());
        }
      }
    }
    return null;
  }

  @Nullable
  private static Pair<GrSignature, String> getTypeToMix(GrAssignmentExpression assignment) {
    GrExpression mixinRef = assignment.getRValue();
    if (mixinRef == null) return null;

    final PsiType type = mixinRef.getType();
    if (type instanceof GrClosureType) {
      final GrSignature signature = ((GrClosureType)type).getSignature();

      final GrExpression lValue = assignment.getLValue();
      assert lValue instanceof GrReferenceExpression;
      final String name = ((GrReferenceExpression)lValue).getReferenceName();

      return Pair.create(signature, name);
    }

    return null;
  }

  /**
   * @return (type[1] in which methods mixed, reference to type[1], type[2] to mixin)
   */
  @Nullable
  private static Trinity<PsiClassType, GrReferenceExpression, PsiClass> getMixinTypes(final GrStatement statement) {
    if (!(statement instanceof GrMethodCall)) return null;

    return CachedValuesManager.getCachedValue(statement, () -> {
      GrMethodCall call = (GrMethodCall)statement;

      Pair<PsiClassType, GrReferenceExpression> original = getTypeToMixIn(call);
      if (original == null) return CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

      PsiClass mix = getTypeToMix(call);
      if (mix == null) return CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

      return CachedValueProvider.Result
        .create(new Trinity<>(original.first, original.second, mix),
                PsiModificationTracker.MODIFICATION_COUNT);
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
    if (!(invoked instanceof GrReferenceExpression)) return null;
    GrReferenceExpression referenceExpression = (GrReferenceExpression)invoked;
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
    return "mixin".equals(name) && containingClass != null && GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName());
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

  public static boolean isCategoryMethod(@NotNull PsiMethod method, @Nullable PsiType qualifierType, @Nullable PsiElement place, @Nullable PsiSubstitutor substitutor) {
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

    if (selfType instanceof PsiClassType &&
        ((PsiClassType)selfType).rawType().equalsToText(CommonClassNames.JAVA_LANG_CLASS) &&
        place instanceof GrReferenceExpression &&
        ((GrReferenceExpression)place).resolve() instanceof PsiClass) {   // ClassType.categoryMethod()  where categoryMethod(Class<> cl, ...)
      final GlobalSearchScope scope = method.getResolveScope();
      final Project project = method.getProject();
      return TypesUtil.isAssignableByMethodCallConversion(selfType, TypesUtil.createJavaLangClassType(qualifierType, project, scope),
                                                          method);
    }
    return TypesUtil.isAssignableByMethodCallConversion(selfType, qualifierType, method);
  }

  @Nullable
  public static PsiClassType getCategoryType(@NotNull final PsiClass categoryAnnotationOwner) {
    return CachedValuesManager.getCachedValue(categoryAnnotationOwner, new CachedValueProvider<PsiClassType>() {
      @Override
      public Result<PsiClassType> compute() {
        return Result.create(inferCategoryType(categoryAnnotationOwner), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
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
}
