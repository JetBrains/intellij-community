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
package org.jetbrains.plugins.groovy.codeInspection.control.finalVar;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GrFieldControlFlowPolicy;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author Max Medvedev
 */
public class GrFinalVariableAccessInspection extends BaseInspection {
  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethod(GrMethod method) {
        super.visitMethod(method);

        final GrOpenBlock block = method.getBlock();
        if (block != null) {
          processLocalVars(block);
        }
      }

      @Override
      public void visitFile(GroovyFileBase file) {
        super.visitFile(file);

        if (file instanceof GroovyFile && file.isScript()) {
          processLocalVars(file);
        }
      }

      @Override
      public void visitField(GrField field) {
        super.visitField(field);
        final GrExpression initializer = field.getInitializerGroovy();
        if (initializer != null) {
          processLocalVars(initializer);
        }

        if (field.hasModifierProperty(PsiModifier.FINAL)) {
          checkFieldIsInitialized(field);
        }
      }


      private void checkFieldIsInitialized(@NotNull GrField field) {
        if (!isFieldInitialized(field)) {
          registerError(field.getNameIdentifierGroovy(), GroovyBundle.message("variable.0.might.not.have.been.initialized", field.getName()),
                        LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }

      @Override
      public void visitClassInitializer(GrClassInitializer initializer) {
        super.visitClassInitializer(initializer);

        final GrOpenBlock block = initializer.getBlock();
        processLocalVars(block);
      }



      private void processLocalVars(GroovyPsiElement scope) {
        MultiMap<PsiElement, GrVariable> scopes = collectVariables(scope);

        for (Map.Entry<PsiElement, Collection<GrVariable>> entry : scopes.entrySet()) {
          final PsiElement scopeToProcess = entry.getKey();

          final Set<GrVariable> forInParameters = ContainerUtil.newHashSet();
          final Map<String, GrVariable> variables = ContainerUtil.newHashMap();
          for (GrVariable var : entry.getValue()) {
            variables.put(var.getName(), var);
            if (var instanceof GrParameter && ((GrParameter)var).getDeclarationScope() instanceof GrForStatement) {
              forInParameters.add(var);
            }
          }

          final List<ReadWriteVariableInstruction> result = new InvalidWriteAccessSearcher().findInvalidWriteAccess(getFlow(scopeToProcess), variables, forInParameters);
          if (result != null) {
            for (ReadWriteVariableInstruction instruction : result) {
              if (variables.containsKey(instruction.getVariableName())) {
                registerError(instruction.getElement(),
                              GroovyBundle.message("cannot.assign.a.value.to.final.field.0", instruction.getVariableName()),
                              LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
              }
            }
          }
        }
      }
    };
  }

  private static boolean isFieldInitialized(@NotNull GrField field) {
    if (field.getInitializerGroovy() != null) return true;

    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final String name = field.getName();

    final GrTypeDefinition aClass = ((GrTypeDefinition)field.getContainingClass());
    if (aClass == null) return true;

    GrClassInitializer[] initializers = aClass.getInitializers();
    for (GrClassInitializer initializer : initializers) {
      if (initializer.isStatic() != isStatic) continue;

      final Instruction[] initializerFlow = buildFlowForField(initializer.getBlock());
      if (VariableInitializationChecker.isVariableDefinitelyInitialized(name, initializerFlow)) {
        return true;
      }
    }

    if (isStatic) return false;

    final GrMethod[] constructors = aClass.getCodeConstructors();
    if (constructors.length == 0) return false;

    Set<GrMethod> initializedConstructors = ContainerUtil.newHashSet();
    Set<GrMethod> notInitializedConstructors = ContainerUtil.newHashSet();

    NEXT_CONSTR:
    for (GrMethod constructor : constructors) {
      if (constructor.getBlock() == null) return false;
      final List<GrMethod> chained = getChainedConstructors(constructor);

      NEXT_CHAINED:
      for (GrMethod method : chained) {
        if (initializedConstructors.contains(method)) {
          continue NEXT_CONSTR;
        }
        else if (notInitializedConstructors.contains(method)) {
          continue NEXT_CHAINED;
        }

        final GrOpenBlock block = method.getBlock();
        assert block != null;
        final boolean initialized = VariableInitializationChecker.isVariableDefinitelyInitialized(name, buildFlowForField(block));

        if (initialized) {
          initializedConstructors.add(method);
          continue NEXT_CONSTR;
        }
        else {
          notInitializedConstructors.add(method);
        }
      }

      return false;
    }
    return true;
  }

  @NotNull
  private static List<GrMethod> getChainedConstructors(@NotNull GrMethod constructor) {
    final HashSet<Object> visited = ContainerUtil.newHashSet();

    final ArrayList<GrMethod> result = ContainerUtil.newArrayList(constructor);
    while (true) {
      final GrConstructorInvocation invocation = PsiUtil.getConstructorInvocation(constructor);
      if (invocation != null && invocation.isThisCall()) {
        final PsiMethod method = invocation.resolveMethod();
        if (method != null && method.isConstructor() && visited.add(method)) {
          result.add((GrMethod)method);
          constructor = (GrMethod)method;
          continue;
        }
      }
      return result;
    }
  }

  @NotNull
  private static Instruction[] buildFlowForField(@NotNull GrOpenBlock block) {
    return new ControlFlowBuilder(block.getProject(), GrFieldControlFlowPolicy.getInstance()).buildControlFlow(block);
  }


  /**
   * @return map: scope -> variables defined in the scope
   */
  @NotNull
  private static MultiMap<PsiElement, GrVariable> collectVariables(@NotNull GroovyPsiElement scope) {
    final MultiMap<PsiElement, GrVariable> scopes = MultiMap.create();
    scope.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitVariable(GrVariable variable) {
        super.visitVariable(variable);
        if (!(variable instanceof PsiField) && variable.hasModifierProperty(PsiModifier.FINAL)) {
          final PsiElement varScope = findScope(variable);
          if (varScope != null) {
            scopes.putValue(varScope, variable);
          }
        }
      }
    });
    return scopes;
  }

  @NotNull
  private static Instruction[] getFlow(@NotNull PsiElement element) {
    return element instanceof GrControlFlowOwner
           ? ((GrControlFlowOwner)element).getControlFlow()
           : new ControlFlowBuilder(element.getProject()).buildControlFlow((GroovyPsiElement)element);
  }


  @Nullable
  private static PsiElement findScope(@NotNull GrVariable variable) {
    GroovyPsiElement result = PsiTreeUtil.getParentOfType(variable, GrControlStatement.class, GrControlFlowOwner.class);
    if (result instanceof GrForStatement) {
      final GrStatement body = ((GrForStatement)result).getBody();
      if (body != null) {
        result = body;
      }
    }
    return result;
  }
}
