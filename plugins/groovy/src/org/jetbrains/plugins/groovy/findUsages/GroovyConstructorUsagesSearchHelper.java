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
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 *         Date: May 2, 2009 3:48:53 PM
 */
public class GroovyConstructorUsagesSearchHelper {
  private GroovyConstructorUsagesSearchHelper() {
  }

  public static boolean execute(final PsiMethod constructor, SearchScope searchScope, final Processor<PsiReference> consumer) {
    if (!constructor.isConstructor()) return true;

    if (searchScope instanceof GlobalSearchScope) {
      searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, GroovyFileType.GROOVY_FILE_TYPE);
    }

    final PsiClass clazz = ApplicationManager.getApplication().runReadAction(new NullableComputable<PsiClass>() {
      public PsiClass compute() {
        return constructor.getContainingClass();
      }
    });
    if (clazz == null) return true;


    //enum constants
    if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (!clazz.isEnum()) return true;
        if (!(clazz instanceof GroovyPsiElement)) return true;
        final PsiField[] fields = clazz.getFields();
        for (PsiField field : fields) {
          if (field instanceof GrEnumConstant) {
            final PsiReference ref = field.getReference();
            if (ref.isReferenceTo(constructor)) {
              if (!consumer.process(ref)) return false;
            }
          }
        }
        return true;
      }
    })) {
      return false;
    }


    ReferencesSearch.search(clazz, searchScope, true).forEach(new ReadActionProcessor<PsiReference>() {
      @Override
      public boolean processInReadAction(PsiReference ref) {
        return processClassReference(ref, constructor, consumer);
      }
    });

    //this()
    if (clazz instanceof GrTypeDefinition) {
      if (!processConstructors(constructor, consumer, clazz, true)) {
        return false;
      }
    }
    //super  : does not work now, need to invent a way for it to work without repository
    if (!DirectClassInheritorsSearch.search(clazz, searchScope).forEach(new Processor<PsiClass>() {
      public boolean process(PsiClass inheritor) {
        if (inheritor instanceof GrTypeDefinition) {
          if (!processConstructors(constructor, consumer, inheritor, false)) return false;
        }
        return true;
      }
    })) {
      return false;
    }

    return true;
  }

  private static boolean processClassReference(PsiReference ref, final PsiMethod constructor, final Processor<PsiReference> consumer) {
    final PsiElement element = ref.getElement();
    if (!(element instanceof GrCodeReferenceElement)) {
      return true;
    }

    final PsiElement parent = element.getParent();
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
        if (cast.getCastTypeElement() == typeElement && !checkListInstantiation(constructor, consumer, cast.getOperand(), typeElement)) {
          return false;
        }
      }
      else if (grandpa instanceof GrSafeCastExpression) {
        final GrSafeCastExpression cast = (GrSafeCastExpression)grandpa;
        if (cast.getCastTypeElement() == typeElement && !checkListInstantiation(constructor, consumer, cast.getOperand(), typeElement)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean checkListInstantiation(PsiMethod constructor,
                                                Processor<PsiReference> consumer,
                                                GrExpression expression, final GrTypeElement typeElement) {
    if (expression instanceof GrListOrMap) {
      final GrListOrMap list = (GrListOrMap)expression;
      if (!list.isMap()) {
        final PsiType expectedType = typeElement.getType();
        final PsiType listType = list.getType();
        if (listType instanceof GrTupleType && expectedType instanceof PsiClassType) {
          final GroovyResolveResult[] candidates = PsiUtil.getConstructorCandidates((PsiClassType)expectedType, ((GrTupleType)listType).getComponentTypes(), list);
          for (GroovyResolveResult candidate : candidates) {
            if (constructor.getManager().areElementsEquivalent(candidate.getElement(), constructor)) {
              if (!consumer.process(PsiReferenceBase.createSelfReference(list, TextRange.from(0, list.getTextLength()), constructor))) {
                return false;
              }
            }
          }
        }
      }
    }
    return true;
  }

  private static boolean processConstructors(final PsiMethod constructor, final Processor<PsiReference> consumer, final PsiClass clazz,
                                             final boolean processThisRefs) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return processClassConstructors(clazz, constructor, consumer, processThisRefs);
      }
    });
  }

  private static boolean processClassConstructors(PsiClass clazz,
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

  private static void processImplicitConstructorCall(final PsiMember usage,
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
