/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.noReturnMethod;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.MaybeReturnInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ThrowingInstruction;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author ven
 */
public class MissingReturnInspection extends GroovySuppressableInspectionTool {
  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroovyInspectionBundle.message("groovy.dfa.issues");
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    return new String[]{"Groovy", getGroupDisplayName()};
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("no.return.display.name");
  }

  public enum ReturnStatus {
    mustReturnValue, shouldReturnValue, shouldNotReturnValue;

    public static ReturnStatus getReturnStatus(PsiElement subject) {
      if (subject instanceof GrClosableBlock) {
        final PsiType inferredReturnType = getExpectedClosureReturnType((GrClosableBlock)subject);
        if (inferredReturnType instanceof PsiClassType) {
          PsiClass resolved = ((PsiClassType)inferredReturnType).resolve();
          if (resolved != null && !(resolved instanceof PsiTypeParameter)) return mustReturnValue;
        }
        return inferredReturnType != null && !PsiType.VOID.equals(inferredReturnType) ? shouldReturnValue : shouldNotReturnValue;
      }
      else if (subject instanceof GrMethod) {
        return ((GrMethod)subject).getReturnTypeElementGroovy() != null && !PsiType.VOID.equals(((GrMethod)subject).getReturnType())
               ? mustReturnValue
               : shouldNotReturnValue;
      }
      return shouldNotReturnValue;
    }
  }

  @Nullable
  public static PsiType getExpectedClosureReturnType(GrClosableBlock closure) {
    List<PsiType> expectedReturnTypes = new ArrayList<>();

    PsiElement parent = closure.getParent();
    if (parent instanceof GrArgumentList && parent.getParent() instanceof GrMethodCall || parent instanceof GrMethodCall) {
      GrMethodCall call = (GrMethodCall)(parent instanceof GrArgumentList ? parent.getParent() : parent);

      GroovyResolveResult[] variants = call.getCallVariants(null);

      for (GroovyResolveResult variant : variants) {
        Map<GrExpression,Pair<PsiParameter,PsiType>> map =
          GrClosureSignatureUtil.mapArgumentsToParameters(variant, closure, true, true, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments());

        if (map != null) {
          Pair<PsiParameter, PsiType> pair = map.get(closure);
          if (pair == null) continue;

          PsiParameter parameter = pair.getFirst();

          PsiType type = parameter.getType();
          if (TypesUtil.isPsiClassTypeToClosure(type)) {
            PsiType[] parameters = ((PsiClassType)type).getParameters();
            if (parameters.length == 1) {
              expectedReturnTypes.add(parameters[0]);
            }
          }
        }
      }
    }
    else {
      for (PsiType expectedType : GroovyExpectedTypesProvider.getDefaultExpectedTypes(closure)) {
        if (TypesUtil.isPsiClassTypeToClosure(expectedType)) {
          PsiType[] parameters = ((PsiClassType)expectedType).getParameters();
          if (parameters.length == 1) {
            expectedReturnTypes.add(parameters[0]);
          }
        }
      }
    }

    for (PsiType type : expectedReturnTypes) {
      if (PsiType.VOID.equals(type)) return PsiType.VOID;
    }
    return TypesUtil.getLeastUpperBoundNullable(expectedReturnTypes, closure.getManager());
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder problemsHolder, boolean onTheFly) {
    return new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      @Override
      public void visitClosure(GrClosableBlock closure) {
        super.visitClosure(closure);
        check(closure, problemsHolder, ReturnStatus.getReturnStatus(closure));
      }

      @Override
      public void visitMethod(GrMethod method) {
        super.visitMethod(method);

        final GrOpenBlock block = method.getBlock();
        if (block != null) {
          check(block, problemsHolder, ReturnStatus.getReturnStatus(method));
        }
      }
    });
  }

  private static void check(GrCodeBlock block, ProblemsHolder holder, ReturnStatus returnStatus) {
    if (methodMissesSomeReturns(block, returnStatus)) {
      addNoReturnMessage(block, holder);
    }
  }

  public static boolean methodMissesSomeReturns(@NotNull GrControlFlowOwner block, @NotNull final ReturnStatus returnStatus) {
    if (returnStatus == ReturnStatus.shouldNotReturnValue) {
      return false;
    }

    final Ref<Boolean> alwaysHaveReturn = new Ref<>(true);
    final Ref<Boolean> sometimesHaveReturn = new Ref<>(false);
    final Ref<Boolean> hasExplicitReturn = new Ref<>(false);
    ControlFlowUtils.visitAllExitPoints(block, new ControlFlowUtils.ExitPointVisitor() {
      @Override
      public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
        //don't modify sometimesHaveReturn  in this case:
        // def foo() {
        //   if (cond) throw new RuntimeException()
        // }
        if (instruction instanceof ThrowingInstruction) {
          if (returnStatus == ReturnStatus.mustReturnValue) {
            sometimesHaveReturn.set(true);
          }
          return true;
        }

        if (instruction instanceof MaybeReturnInstruction && ((MaybeReturnInstruction)instruction).mayReturnValue()) {
          sometimesHaveReturn.set(true);
          return true;
        }

        if (instruction.getElement() instanceof GrReturnStatement && returnValue != null) {
          sometimesHaveReturn.set(true);
          hasExplicitReturn.set(true);
          return true;
        }

        alwaysHaveReturn.set(false);
        return true;
      }
    });

    if (returnStatus == ReturnStatus.mustReturnValue && !sometimesHaveReturn.get()) {
      return true;
    }

    return sometimesHaveReturn.get() && !alwaysHaveReturn.get();
  }

  private static void addNoReturnMessage(GrCodeBlock block, ProblemsHolder holder) {
    final PsiElement lastChild = block.getLastChild();
    if (lastChild == null) return;
    TextRange range = lastChild.getTextRange();
    if (!lastChild.isValid() || !lastChild.isPhysical() || range.getStartOffset() >= range.getEndOffset()) {
      return;
    }
    holder.registerProblem(lastChild, GroovyInspectionBundle.message("no.return.message"));
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "GroovyMissingReturnStatement";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
