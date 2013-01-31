/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.diagnostic.Logger;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceResolveUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.resolve.noncode.MixinMemberContributor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GdkMethodUtil {

  private static final Logger LOG = Logger.getInstance(GdkMethodUtil.class);

  public static final Set<String> COLLECTION_METHOD_NAMES = ContainerUtil.newHashSet(
    "each", "eachWithIndex", "any", "every", "reverseEach", "collect", "collectAll", "find", "findAll", "retainAll", "removeAll", "split",
    "groupBy", "groupEntriesBy", "findLastIndexOf", "findIndexValues", "findIndexOf"
  );
  @NonNls private static final String WITH = "with";
  @NonNls private static final String IDENTITY = "identity";

  @NonNls public static final String USE = "use";
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

  public static boolean categoryIteration(GrClosableBlock place, final PsiScopeProcessor processor, ResolveState state) {
    final ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.METHOD)) return true;
    
    final GrMethodCall call = checkMethodCall(place, USE);
    if (call == null) return true;

    final GrClosableBlock[] closures = call.getClosureArguments();
    final GrExpression[] args = call.getExpressionArguments();
    if (!(placeEqualsSingleClosureArg(place, closures) || placeEqualsLastArg(place, args))) return true;

    if (!(call.resolveMethod() instanceof GrGdkMethod)) return true;

    state = state.put(ResolverProcessor.RESOLVE_CONTEXT, call);
    for (GrExpression arg : args) {
      if (arg instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)arg).resolve();
        if (resolved instanceof PsiClass) {
          if (!processCategoryMethods(place, processor, state, (PsiClass)resolved)) return false;
        }
      }
    }
    return true;
  }

  private static boolean placeEqualsLastArg(GrClosableBlock place, GrExpression[] args) {
    return args.length > 0 && place.equals(args[args.length - 1]);
  }

  private static boolean placeEqualsSingleClosureArg(GrClosableBlock place, GrClosableBlock[] closures) {
    return closures.length == 1 && place.equals(closures[0]);
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
    final DelegatingScopeProcessor delegate = new DelegatingScopeProcessor(processor) {
      @Override
      public boolean execute(@NotNull PsiElement element, ResolveState delegateState) {
        if (element instanceof PsiMethod && isCategoryMethod((PsiMethod)element, null, null, null)) {
          PsiMethod method = (PsiMethod)element;
          return processor.execute(GrGdkMethodImpl.createGdkMethod(method, false, generateOriginInfo(method)), delegateState);
        }
        return true;
      }
    };
    return categoryClass.processDeclarations(delegate, state, null, place);
  }

  public static boolean withIteration(GrClosableBlock block, final PsiScopeProcessor processor) {
    GrMethodCall call = checkMethodCall(block, WITH);
    if (call == null) {
      call = checkMethodCall(block, IDENTITY);
    }
    if (call == null) return true;
    final GrExpression invoked = call.getInvokedExpression();
    LOG.assertTrue(invoked instanceof GrReferenceExpression);
    final GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();
    if (qualifier == null) return true;
    if (!GrReferenceResolveUtil.processQualifier(processor, qualifier, (GrReferenceExpression)invoked)) return false;

    return true;
  }

  @Nullable
  private static GrMethodCall checkMethodCall(GrClosableBlock place, String methodName) {
    final PsiElement context = place.getContext();
    GrMethodCall call = null;
    if (context instanceof GrMethodCall) {
      call = (GrMethodCall)context;
    }
    else if (context instanceof GrArgumentList) {
      final PsiElement ccontext = context.getContext();
      if (ccontext instanceof GrMethodCall) {
        call = (GrMethodCall)ccontext;
      }
    }
    if (call == null) return null;
    final GrExpression invoked = call.getInvokedExpression();
    if (!(invoked instanceof GrReferenceExpression) || !methodName.equals(((GrReferenceExpression)invoked).getReferenceName())) {
      return null;
    }
    return call;
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

        final DelegatingScopeProcessor delegate = new MixinMemberContributor.MixinProcessor(processor, subjectType, qualifier);
        mixin.processDeclarations(delegate, state, null, place);
      }
    }

    return true;
  }

  @Nullable
  private static Trinity<PsiClassType, GrReferenceExpression, PsiClass> getMixinTypes(final GrStatement statement) {
    if (!(statement instanceof GrMethodCall)) return null;

    final CachedValuesManager manager = CachedValuesManager.getManager(statement.getProject());
    return manager.getCachedValue(statement, new CachedValueProvider<Trinity<PsiClassType, GrReferenceExpression, PsiClass>>() {
      @Nullable
      @Override
      public Result<Trinity<PsiClassType, GrReferenceExpression, PsiClass>> compute() {
        GrMethodCall call = (GrMethodCall)statement;

        Pair<PsiClassType, GrReferenceExpression> original = getTypeToMixIn(call);
        if (original == null) return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

        PsiClass mix = getTypeToMix(call);
        if (mix == null) return Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);

        return Result.create(new Trinity<PsiClassType, GrReferenceExpression, PsiClass>(original.first, original.second, mix),
                             PsiModificationTracker.MODIFICATION_COUNT);
      }
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

  private static boolean isSingleExpressionArg(GrMethodCall call) {
    return call.getExpressionArguments().length == 1 &&
           !PsiImplUtil.hasNamedArguments(call.getArgumentList()) &&
           !PsiImplUtil.hasClosureArguments(call);
  }

  @Nullable
  private static Pair<PsiClassType, GrReferenceExpression> getTypeToMixIn(GrMethodCall methodCall) {
    GrExpression invoked = methodCall.getInvokedExpression();
    if (invoked instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)invoked).resolve();
      if (resolved instanceof PsiMethod && isMixinMethod((PsiMethod)resolved)) {
        GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();
        Pair<PsiClassType, GrReferenceExpression> type = getPsiClassFromReference(qualifier);
        if (type != null) {
          return type;
        }
        if (qualifier == null) {
          qualifier = PsiImplUtil.getRuntimeQualifier((GrReferenceExpression)invoked);
        }
        if (qualifier != null && isMetaClass(qualifier.getType())) {
          if (qualifier instanceof GrMethodCall) qualifier = ((GrMethodCall)qualifier).getInvokedExpression();

          if (qualifier instanceof GrReferenceExpression) {
            GrExpression qqualifier = ((GrReferenceExpression)qualifier).getQualifier();
            if (qqualifier != null) {
              Pair<PsiClassType, GrReferenceExpression> type1 = getPsiClassFromReference(qqualifier);
              if (type1 != null) {
                return type1;
              }
            }
            else {
              PsiType qtype = GrReferenceResolveUtil.getQualifierType((GrReferenceExpression)qualifier);
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

  private static boolean isMixinMethod(PsiMethod method) {
    if (method instanceof GrGdkMethod) method = ((GrGdkMethod)method).getStaticMethod();
    PsiClass containingClass = method.getContainingClass();
    String name = method.getName();
    return "mixin".equals(name) && containingClass != null && GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName());
  }

  private static boolean isMetaClass(PsiType qualifierType) {
    return qualifierType != null && qualifierType.equalsToText(GroovyCommonClassNames.GROOVY_LANG_META_CLASS);
  }

  private static boolean isClassRef(GrExpression mixinRef) {
    return mixinRef instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)mixinRef).getReferenceName());
  }

  @Nullable
  private static Pair<PsiClassType, GrReferenceExpression> getPsiClassFromReference(GrExpression ref) {
    if (isClassRef(ref)) ref = ((GrReferenceExpression)ref).getQualifier();
    if (ref instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)ref).resolve();
      if (resolved instanceof PsiClass) {
        PsiType type = ref.getType();
        LOG.assertTrue(type instanceof PsiClassType, "reference resolved into PsiClass should have PsiClassType");
        return Pair.create((PsiClassType)type, (GrReferenceExpression)ref);
      }
    }
    return null;
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
    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(categoryAnnotationOwner.getProject());
    return cachedValuesManager.getCachedValue(categoryAnnotationOwner, new CachedValueProvider<PsiClassType>() {
      @Override
      public Result<PsiClassType> compute() {
        return Result.create(inferCategoryType(categoryAnnotationOwner), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }

      @Nullable
      private PsiClassType inferCategoryType(final PsiClass aClass) {
        return RecursionManager.doPreventingRecursion(aClass, true, new NullableComputable<PsiClassType>() {
          @Nullable
          @Override
          public PsiClassType compute() {
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
          }
        });
      }
    });
  }
}
