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
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

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
      }

      @Override
      public void visitClassInitializer(GrClassInitializer initializer) {
        super.visitClassInitializer(initializer);

        processLocalVars(initializer);
      }

      private void processLocalVars(GroovyPsiElement scope) {
        MultiMap<PsiElement, GrVariable> scopes = collectVariables(scope);

        for (Map.Entry<PsiElement, Collection<GrVariable>> entry : scopes.entrySet()) {
          final PsiElement scopeToProcess = entry.getKey();

          final Map<String, GrVariable> variables = ContainerUtil.newHashMap();
          for (GrVariable var : entry.getValue()) {
            variables.put(var.getName(), var);
          }


          final List<ReadWriteVariableInstruction> result = checkFlow(getFlow(scopeToProcess), variables);
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
  private static List<ReadWriteVariableInstruction> checkFlow(@NotNull Instruction[] flow, @NotNull Map<String, GrVariable> variables) {
    DFAEngine<MyData> engine = new DFAEngine<MyData>(flow, new MyDFAInstance(), new MySemilattice());
    final ArrayList<MyData> dfaResult = engine.performDFAWithTimeout();
    if (dfaResult == null) return null;


    List<ReadWriteVariableInstruction> result = ContainerUtil.newArrayList();
    for (int i = 0; i < flow.length; i++) {
      Instruction instruction = flow[i];
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction)instruction).isWrite()) {
        final MyData initialized = dfaResult.get(i);
        final GrVariable var = variables.get(((ReadWriteVariableInstruction)instruction).getVariableName());
        if (var instanceof GrParameter && ((GrParameter)var).getDeclarationScope() instanceof GrForStatement) {
          if (initialized.isInitialized(((ReadWriteVariableInstruction)instruction).getVariableName())) {
            result.add((ReadWriteVariableInstruction)instruction);
          }
        }
        else {
          if (initialized.isOverInitialized(((ReadWriteVariableInstruction)instruction).getVariableName())) {
            result.add((ReadWriteVariableInstruction)instruction);
          }
        }
      }
    }


    return result;
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

  private static class MyDFAInstance implements DfaInstance<MyData> {
    @Override
    public void fun(MyData e, Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction)instruction).isWrite()) {
        e.add(((ReadWriteVariableInstruction)instruction).getVariableName());
      }
    }

    @NotNull
    @Override
    public MyData initial() {
      return new MyData();
    }

    @Override
    public boolean isForward() {
      return true;
    }
  }

  private static class MySemilattice implements Semilattice<MyData> {
    @Override
    public MyData join(ArrayList<MyData> ins) {
      return new MyData(ins);
    }

    @Override
    public boolean eq(MyData e1, MyData e2) {
      return e1.equals(e2);
    }
  }

  private static class MyData {
    private final Set<String> myInitialized = ContainerUtil.newHashSet();
    private final Set<String> myOverInitialized = ContainerUtil.newHashSet();

    public MyData(List<MyData> ins) {
      for (MyData data : ins) {
        myInitialized.addAll(data.myInitialized);
        myOverInitialized.addAll(data.myOverInitialized);
      }
    }

    public MyData() {}

    public void add(String var) {
      if (!myInitialized.add(var)) {
        myOverInitialized.add(var);
      }
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyData &&
             myInitialized.equals(((MyData)obj).myInitialized) &&
             myOverInitialized.equals(((MyData)obj).myOverInitialized);
    }

    public boolean isOverInitialized(String var) {
      return myOverInitialized.contains(var);
    }

    public boolean isInitialized(String var) {
      return myInitialized.contains(var);
    }
  }
}
