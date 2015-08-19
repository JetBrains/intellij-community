/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyConstructorUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  public GroovyConstructorUsagesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters p, @NotNull Processor<PsiReference> consumer) {
    processConstructorUsages(p.getMethod(), p.getEffectiveSearchScope(), consumer, p.getOptimizer(), !p.isStrictSignatureSearch());
  }

  public static final Key<Set<PsiClass>> LITERALLY_CONSTRUCTED_CLASSES = Key.create("LITERALLY_CONSTRUCTED_CLASSES");
  static void processConstructorUsages(final PsiMethod constructor,
                                       final SearchScope searchScope,
                                       final Processor<PsiReference> consumer,
                                       final SearchRequestCollector collector,
                                       final boolean includeOverloads) {
    if (!constructor.isConstructor()) return;

    final PsiClass clazz = constructor.getContainingClass();
    if (clazz == null) return;

    SearchScope onlyGroovy = GroovyScopeUtil.restrictScopeToGroovyFiles(searchScope, GroovyScopeUtil.getEffectiveScope(constructor));
    Set<PsiClass> processed = collector.getSearchSession().getUserData(LITERALLY_CONSTRUCTED_CLASSES);
    if (processed == null) {
      collector.getSearchSession().putUserData(LITERALLY_CONSTRUCTED_CLASSES, processed = ContainerUtil.newConcurrentSet());
    }
    if (!processed.add(clazz)) return;

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

    final LiteralConstructorSearcher literalProcessor = new LiteralConstructorSearcher(constructor, consumer, includeOverloads);

    final Processor<GrNewExpression> newExpressionProcessor = new Processor<GrNewExpression>() {
      @Override
      public boolean process(GrNewExpression grNewExpression) {
        final PsiMethod resolvedConstructor = grNewExpression.resolveMethod();
        if (includeOverloads || constructor.getManager().areElementsEquivalent(resolvedConstructor, constructor)) {
          return consumer.process(grNewExpression.getReferenceElement());
        }
        return true;
      }
    };

    processGroovyClassUsages(clazz, searchScope, collector, newExpressionProcessor, literalProcessor);

    //this()
    if (clazz instanceof GrTypeDefinition) {
      if (!processConstructors(constructor, consumer, clazz, true)) {
        return;
      }
    }
    //super()
    DirectClassInheritorsSearch.search(clazz, onlyGroovy).forEach(new ReadActionProcessor<PsiClass>() {
      @Override
      public boolean processInReadAction(PsiClass inheritor) {
        if (inheritor instanceof GrTypeDefinition) {
          if (!processConstructors(constructor, consumer, inheritor, false)) return false;
        }
        return true;
      }
    });
  }

  public static void processGroovyClassUsages(final PsiClass clazz,
                                              final SearchScope scope,
                                              SearchRequestCollector collector,
                                              final Processor<GrNewExpression> newExpressionProcessor,
                                              final LiteralConstructorSearcher literalProcessor) {
    ReferencesSearch.searchOptimized(clazz, scope, false, collector, true, new PairProcessor<PsiReference, SearchRequestCollector>() {
      @Override
      public boolean process(PsiReference ref, SearchRequestCollector collector) {
        final PsiElement element = ref.getElement();

        if (element instanceof GrCodeReferenceElement) {
          if (!processGroovyConstructorUsages((GrCodeReferenceElement)element, newExpressionProcessor, literalProcessor)) {
            return false;
          }
        }

        return true;
      }
    });
  }

  private static boolean processGroovyConstructorUsages(GrCodeReferenceElement element,
                                                        final Processor<GrNewExpression> newExpressionProcessor,
                                                        final LiteralConstructorSearcher literalProcessor) {
    PsiElement parent = element.getParent();

    if (parent instanceof GrAnonymousClassDefinition) {
      parent = parent.getParent();
    }
    if (parent instanceof GrNewExpression) {
      return newExpressionProcessor.process((GrNewExpression)parent);
    }

    if (parent instanceof GrTypeElement) {
      final GrTypeElement typeElement = (GrTypeElement)parent;

      final PsiElement grandpa = typeElement.getParent();
      if (grandpa instanceof GrVariableDeclaration) {
        final GrVariable[] vars = ((GrVariableDeclaration)grandpa).getVariables();
        if (vars.length == 1) {
          final GrVariable variable = vars[0];
          if (!checkLiteralInstantiation(variable.getInitializerGroovy(), literalProcessor)) {
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
              if (!checkLiteralInstantiation(returnValue, literalProcessor)) {
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
            !checkLiteralInstantiation(cast.getOperand(), literalProcessor)) {
          return false;
        }
      }
      else if (grandpa instanceof GrSafeCastExpression) {
        final GrSafeCastExpression cast = (GrSafeCastExpression)grandpa;
        if (cast.getCastTypeElement() == typeElement &&
            !checkLiteralInstantiation(cast.getOperand(), literalProcessor)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean checkLiteralInstantiation(GrExpression expression,
                                                   final LiteralConstructorSearcher literalProcessor) {

    if (expression instanceof GrListOrMap) {
      return literalProcessor.processLiteral((GrListOrMap)expression);
    }
    return true;
  }

  private static boolean processConstructors(final PsiMethod searchedConstructor, final Processor<PsiReference> consumer, final PsiClass clazz,
                                             final boolean processThisRefs) {
    final PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length == 0) {
      processImplicitConstructorCall(clazz, consumer, searchedConstructor);
    }
    for (PsiMethod constructor : constructors) {
      if (!(constructor instanceof GrMethod)) continue;
      final GrOpenBlock block = ((GrMethod)constructor).getBlock();
      if (block != null) {
        final GrStatement[] statements = block.getStatements();
        if (statements.length > 0 && statements[0] instanceof GrConstructorInvocation) {
          final GrConstructorInvocation invocation = (GrConstructorInvocation)statements[0];
          if (invocation.isThisCall() == processThisRefs &&
              invocation.getManager().areElementsEquivalent(invocation.resolveMethod(), searchedConstructor) &&
              !consumer.process(invocation.getInvokedExpression())) {
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

  private static void processImplicitConstructorCall(@NotNull final PsiMember usage,
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
      @Override
      public PsiElement getElement() {
        return usage;
      }

      @Override
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
