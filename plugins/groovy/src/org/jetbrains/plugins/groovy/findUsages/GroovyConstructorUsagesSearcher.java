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

package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.gpp.GppTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyConstructorUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  public GroovyConstructorUsagesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(MethodReferencesSearch.SearchParameters p, Processor<PsiReference> consumer) {
    processConstructorUsages(p.getMethod(), p.getScope(), consumer, p.getOptimizer());
  }

  static void processConstructorUsages(final PsiMethod constructor, final SearchScope searchScope, final Processor<PsiReference> consumer, final SearchRequestCollector collector) {
    if (!constructor.isConstructor()) return;

    SearchScope onlyGroovy = searchScope;
    if (onlyGroovy instanceof GlobalSearchScope) {
      onlyGroovy = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)onlyGroovy, GroovyFileType.GROOVY_FILE_TYPE);
    }

    final PsiClass clazz = constructor.getContainingClass();
    if (clazz == null) return;


    if (clazz.isEnum() && clazz instanceof GroovyPsiElement) {
      for (PsiField field : clazz.getFields()) {
        if (field instanceof GrEnumConstant) {
          final PsiReference ref = field.getReference();
          if (ref != null && ref.isReferenceTo(constructor)) {
            if (!consumer.process(ref)) return;
          }
        }
      }
    }

    final Set<PsiMethod> processedMethods = new ConcurrentHashSet<PsiMethod>();

    ReferencesSearch.searchOptimized(clazz, searchScope, true, collector, true, new PairProcessor<PsiReference, SearchRequestCollector>() {
      @Override
      public boolean process(PsiReference ref, SearchRequestCollector collector) {
        return processClassReference(ref, clazz, constructor, consumer, processedMethods, searchScope, collector);
      }
    });

    //this()
    if (clazz instanceof GrTypeDefinition) {
      if (!processConstructors(constructor, consumer, clazz, true)) {
        return;
      }
    }
    //super()
    DirectClassInheritorsSearch.search(clazz, onlyGroovy).forEach(new Processor<PsiClass>() {
      public boolean process(PsiClass inheritor) {
        if (inheritor instanceof GrTypeDefinition) {
          if (!processConstructors(constructor, consumer, inheritor, false)) return false;
        }
        return true;
      }
    });
  }

  @Nullable
  static PsiMethod getMethodToSearchForCallsWithLiteralArguments(PsiElement element, PsiClass clazz, Set<PsiMethod> processedMethods) {
    final PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (parameter != null) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
      if (method != null && processedMethods.add(method) && Arrays.asList(method.getParameterList().getParameters()).contains(parameter)) {
        final PsiType parameterType = parameter.getType();
        if (parameterType instanceof PsiClassType) {
          if (method.getManager().areElementsEquivalent(clazz, ((PsiClassType)parameterType).resolve())) {
            return method;
          }
        }
      }
    }
    return null;
  }

  static boolean processClassReference(final PsiReference ref,
                                               final PsiClass clazz,
                                               final PsiMethod constructor,
                                               final Processor<PsiReference> consumer,
                                               final Set<PsiMethod> processedMethods, SearchScope scope, SearchRequestCollector collector) {
    final PsiElement element = ref.getElement();
    if (element instanceof GrCodeReferenceElement) {
      if (!processGroovyConstructorUsages((GrCodeReferenceElement)element, constructor, consumer, ref)) {
        return false;
      }

    }

    final PsiMethod method = getMethodToSearchForCallsWithLiteralArguments(element, clazz, processedMethods);
    if (method != null) {
      final GlobalSearchScope gppScope = getGppScope(clazz.getProject());
      MethodReferencesSearch.searchOptimized(method, gppScope.intersectWith(scope), true, collector, true, new PairProcessor<PsiReference, SearchRequestCollector>() {
        @Override
        public boolean process(PsiReference psiReference, SearchRequestCollector collector) {
          if (psiReference instanceof GrReferenceElement) {
            final PsiElement parent = ((GrReferenceElement)psiReference).getParent();
            if (parent instanceof GrCall) {
              final GrArgumentList argList = ((GrCall)parent).getArgumentList();
              if (argList != null) {
                boolean checkedTypedContext = false;

                for (GrExpression argument : argList.getExpressionArguments()) {
                  if (argument instanceof GrListOrMap && !((GrListOrMap)argument).isMap()) {
                    if (!checkedTypedContext) {
                      if (!GppTypeConverter.hasTypedContext(parent)) {
                        return true;
                      }
                      checkedTypedContext = true;
                    }

                    for (PsiType psiType : GroovyExpectedTypesProvider.getDefaultExpectedTypes(argument)) {
                      if (psiType instanceof PsiClassType &&
                          clazz.getManager().areElementsEquivalent(clazz,((PsiClassType)psiType).resolve()) &&
                          !checkListInstantiation(constructor, consumer, (GrListOrMap)argument, (PsiClassType)psiType)) {
                        return false;
                      }
                    }
                  }
                }
              }
            }
          }
          return true;
        }
      });
    }

    return true;
  }

  static GlobalSearchScope getGppScope(final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, new CachedValueProvider<GlobalSearchScope>() {
      @Override
      public Result<GlobalSearchScope> compute() {
        return Result.create(calcGppScope(project), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
      }
    });
  }

  static boolean processGroovyConstructorUsages(GrCodeReferenceElement element,
                                                        final PsiMethod constructor,
                                                        final Processor<PsiReference> consumer,
                                                        PsiReference ref) {
    PsiElement parent = element.getParent();

    if (parent instanceof GrAnonymousClassDefinition) {
      parent = parent.getParent();
    }
    if (parent instanceof GrNewExpression) {
      final PsiMethod resolvedConstructor = ((GrNewExpression)parent).resolveConstructor();
      if (constructor.getManager().areElementsEquivalent(resolvedConstructor, constructor) && !consumer.process(ref)) {
        return false;
      }
    }
    else if (parent instanceof GrTypeElement) {
      final GrTypeElement typeElement = (GrTypeElement)parent;

      final PsiElement grandpa = typeElement.getParent();
      if (grandpa instanceof GrVariableDeclaration) {
        final GrVariable[] vars = ((GrVariableDeclaration)grandpa).getVariables();
        if (vars.length == 1) {
          final GrVariable variable = vars[0];
          if (!checkListInstantiation(constructor, consumer, variable.getInitializerGroovy(), typeElement)) {
            return false;
          }
        }
      }
      else if (grandpa instanceof GrMethod) {
        final GrMethod method = (GrMethod)grandpa;
        if (typeElement == method.getReturnTypeElementGroovy()) {
          ControlFlowUtils.visitAllExitPoints(method.getBlock(), new ControlFlowUtils.ExitPointVisitor() {
            @Override
            public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
              if (!checkListInstantiation(constructor, consumer, returnValue, typeElement)) {
                return false;
              }
              return true;
            }
          });
        }
      }
      else if (grandpa instanceof GrTypeCastExpression) {
        final GrTypeCastExpression cast = (GrTypeCastExpression)grandpa;
        if (cast.getCastTypeElement() == typeElement &&
            !checkListInstantiation(constructor, consumer, cast.getOperand(), typeElement)) {
          return false;
        }
      }
      else if (grandpa instanceof GrSafeCastExpression) {
        final GrSafeCastExpression cast = (GrSafeCastExpression)grandpa;
        if (cast.getCastTypeElement() == typeElement &&
            !checkListInstantiation(constructor, consumer, cast.getOperand(), typeElement)) {
          return false;
        }
      }
    }
    return true;
  }

  static GlobalSearchScope calcGppScope(Project project) {
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    final GlobalSearchScope maximal = GlobalSearchScope.getScopeRestrictedByFileTypes(allScope, GroovyFileType.GROOVY_FILE_TYPE);
    GlobalSearchScope gppExtensions = new DelegatingGlobalSearchScope(maximal) {
      @Override
      public boolean contains(VirtualFile file) {
        return super.contains(file) && GppTypeConverter.isGppExtension(file.getExtension());
      }
    };
    final PsiClass typed = JavaPsiFacade.getInstance(project).findClass(GppTypeConverter.GROOVY_LANG_TYPED, allScope);
    if (typed != null) {
      final Set<VirtualFile> files = new HashSet<VirtualFile>();
      AnnotatedElementsSearch.searchElements(typed, maximal, PsiModifierListOwner.class).forEach(new Processor<PsiModifierListOwner>() {
        @Override
        public boolean process(PsiModifierListOwner occurrence) {
          ContainerUtil.addIfNotNull(occurrence.getContainingFile().getVirtualFile(), files);
          return true;
        }
      });

      GlobalSearchScope withTypedAnno = GlobalSearchScope.filesScope(project, files);
      return withTypedAnno.union(gppExtensions);
    }

    return gppExtensions;
  }

  static boolean checkListInstantiation(PsiMethod constructor,
                                                Processor<PsiReference> consumer,
                                                GrExpression expression, final GrTypeElement typeElement) {
    if (expression instanceof GrListOrMap) {
      final GrListOrMap list = (GrListOrMap)expression;
      if (!list.isMap()) {
        final PsiType expectedType = typeElement.getType();
        if (expectedType instanceof PsiClassType) {
          return checkListInstantiation(constructor, consumer, list, (PsiClassType)expectedType);
        }
      }
    }
    return true;
  }

  static boolean checkListInstantiation(PsiMethod constructor,
                                                Processor<PsiReference> consumer,
                                                GrListOrMap list,
                                                PsiClassType expectedType) {
    final PsiType listType = list.getType();
    if (listType instanceof GrTupleType) {
      for (GroovyResolveResult candidate : PsiUtil.getConstructorCandidates(expectedType, ((GrTupleType)listType).getComponentTypes(), list)) {
        if (constructor.getManager().areElementsEquivalent(candidate.getElement(), constructor)) {
          if (!consumer.process(PsiReferenceBase.createSelfReference(list, TextRange.from(0, list.getTextLength()), constructor))) {
            return false;
          }
        }
      }
    }
    return true;
  }

  static boolean processConstructors(final PsiMethod constructor, final Processor<PsiReference> consumer, final PsiClass clazz,
                                             final boolean processThisRefs) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return processClassConstructors(clazz, constructor, consumer, processThisRefs);
      }
    });
  }

  static boolean processClassConstructors(PsiClass clazz,
                                                  PsiMethod searchedConstructor,
                                                  Processor<PsiReference> consumer,
                                                  boolean processThisRefs) {
    final PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length == 0) {
      processImplicitConstructorCall(clazz, consumer, searchedConstructor);
    }
    for (PsiMethod constructor : constructors) {
      final GrOpenBlock block = ((GrMethod)constructor).getBlock();
      if (block != null) {
        final GrStatement[] statements = block.getStatements();
        if (statements.length > 0 && statements[0] instanceof GrConstructorInvocation) {
          final GrConstructorInvocation invocation = (GrConstructorInvocation)statements[0];
          if (invocation.isThisCall() == processThisRefs &&
              invocation.getManager().areElementsEquivalent(invocation.resolveConstructor(), searchedConstructor) &&
              !consumer.process(invocation)) {
            return false;
          }
        }
        else {
          processImplicitConstructorCall(constructor, consumer, searchedConstructor);
        }
      }
    }
    return true;
  }

  static void processImplicitConstructorCall(final PsiMember usage,
                                                     final Processor<PsiReference> processor,
                                                     final PsiMethod constructor) {
    if (constructor instanceof GrMethod) {
      GrParameter[] grParameters = (GrParameter[])constructor.getParameterList().getParameters();
      if (grParameters.length > 0 && !grParameters[0].isOptional()) return;
    }
    else if (constructor.getParameterList().getParameters().length > 0) return;


    PsiManager manager = constructor.getManager();
    if (manager.areElementsEquivalent(usage, constructor) || manager.areElementsEquivalent(constructor.getContainingClass(), usage.getContainingClass())) return;
    processor.process(new LightMemberReference(manager, usage, PsiSubstitutor.EMPTY) {
      public PsiElement getElement() {
        return usage;
      }

      public TextRange getRangeInElement() {
        if (usage instanceof PsiClass) {
          PsiIdentifier identifier = ((PsiClass)usage).getNameIdentifier();
          if (identifier != null) return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
        }
        else if (usage instanceof PsiMethod) {
          PsiIdentifier identifier = ((PsiMethod)usage).getNameIdentifier();
          if (identifier != null) return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
        }
        return super.getRangeInElement();
      }
    });

  }

}
