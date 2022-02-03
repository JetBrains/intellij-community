// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.unusedDef;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionBase;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 & @author ven
 */
public final class UnusedDefInspection extends GroovyLocalInspectionBase {
  private static final Logger LOG = Logger.getInstance(UnusedDefInspection.class);

  @NotNull
  @Override
  public String getShortName() {
    // used to enable inspection in tests
    // remove when inspection class will match its short name
    return "GroovyUnusedAssignment";
  }

  @Override
  protected void check(@NotNull final GrControlFlowOwner owner, @NotNull final ProblemsHolder problemsHolder) {
    final Instruction[] flow = owner.getControlFlow();
    final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance();
    final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    final DFAEngine<DefinitionMap> engine = new DFAEngine<>(flow, dfaInstance, lattice);
    final List<DefinitionMap> dfaResult = engine.performDFAWithTimeout();
    if (dfaResult == null) {
      return;
    }

    final IntSet unusedDefs = new IntOpenHashSet();
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
        unusedDefs.add(instruction.num());
      }
    }

    for (int i = 0; i < dfaResult.size(); i++) {
      final Instruction instruction = flow[i];
      if (instruction instanceof ReadWriteVariableInstruction) {
        final ReadWriteVariableInstruction varInst = (ReadWriteVariableInstruction) instruction;
        if (!varInst.isWrite()) {
          final int descriptor = varInst.getDescriptor();
          DefinitionMap e = dfaResult.get(i);
          if (e == null) {
            continue;
          }
          e.forEachValue(reaching -> {
            reaching.forEach((IntConsumer)defNum -> {
              final int defDescriptor = ((ReadWriteVariableInstruction)flow[defNum]).getDescriptor();
              if (descriptor == defDescriptor) {
                unusedDefs.remove(defNum);
              }
            });
          });
        }
      }
    }

    final Set<PsiElement> checked = new HashSet<>();

    unusedDefs.forEach((IntConsumer)num -> {
      final ReadWriteVariableInstruction instruction = (ReadWriteVariableInstruction)flow[num];
      final PsiElement element = instruction.getElement();
      process(element, checked, problemsHolder, GroovyBundle.message("unused.assignment.tooltip"));
    });

    owner.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof GrControlFlowOwner) {
          // don't go deeper
        }
        else if (element instanceof GrVariable && !(element instanceof GrField)) {
          GrVariable variable = (GrVariable)element;
          if (checked.contains(variable) || variable.getInitializerGroovy() != null) return;
          if (ReferencesSearch.search(variable, variable.getUseScope()).findFirst() == null) {
            process(variable, checked, problemsHolder, GroovyBundle.message("unused.variable"));
          }
        }
        else {
          super.visitElement(element);
        }
      }
    });
  }

  private static void process(@Nullable PsiElement element,
                              Set<PsiElement> checked,
                              ProblemsHolder problemsHolder,
                              final @InspectionMessage String message) {
    if (element == null) return;
    if (!checked.add(element)) return;
    if (isLocalAssignment(element) && isUsedInTopLevelFlowOnly(element) && !isIncOrDec(element)) {
      PsiElement toHighlight = getHighlightElement(element);
      problemsHolder.registerProblem(toHighlight, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }
  }

  private static PsiElement getHighlightElement(PsiElement element) {
    PsiElement toHighlight = null;
    if (element instanceof GrReferenceExpression) {
      PsiElement parent = element.getParent();
      if (parent instanceof GrAssignmentExpression) {
        toHighlight = ((GrAssignmentExpression)parent).getLValue();
      }
      if (parent instanceof GrUnaryExpression && ((GrUnaryExpression)parent).isPostfix()) {
        toHighlight = parent;
      }
    }
    else if (element instanceof GrVariable) {
      toHighlight = ((GrVariable)element).getNameIdentifierGroovy();
    }
    if (toHighlight == null) toHighlight = element;
    return toHighlight;
  }

  private static boolean isIncOrDec(PsiElement element) {
    PsiElement parent = element.getParent();
    if (!(parent instanceof GrUnaryExpression)) return false;

    IElementType type = ((GrUnaryExpression)parent).getOperationTokenType();
    return type == GroovyTokenTypes.mINC || type == GroovyTokenTypes.mDEC;
  }

  private static boolean isUsedInTopLevelFlowOnly(PsiElement element) {
    GrVariable var = null;
    if (element instanceof GrVariable) {
      var = (GrVariable)element;
    }
    else if (element instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)element).resolve();
      if (resolved instanceof GrVariable) var = (GrVariable)resolved;
    }

    if (var != null) {
      final GroovyPsiElement scope = ControlFlowUtils.findControlFlowOwner(var);
      if (scope == null) {
        PsiFile file = var.getContainingFile();
        if (file == null) {
          LOG.error("no file??? var of type" + var.getClass().getCanonicalName());
        }
        else {
          TextRange range = var.getTextRange();
          LOG.error("var: " + var.getName() + ", offset:" + (range != null ? range.getStartOffset() : -1));
        }
        return false;
      }

      return ReferencesSearch.search(var, var.getUseScope()).forEach(
        ref -> ControlFlowUtils.findControlFlowOwner(ref.getElement()) == scope);
    }

    return true;
  }


  private static boolean isLocalAssignment(PsiElement element) {
    if (element instanceof GrVariable) {
      return isLocalVariable((GrVariable)element, false);
    }
    else if (element instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)element).resolve();
      return resolved instanceof GrVariable && isLocalVariable((GrVariable)resolved, true);
    }

    return false;
  }

  private static boolean isLocalVariable(GrVariable var, boolean parametersAllowed) {
    return !(var instanceof GrField || var instanceof GrParameter && !parametersAllowed);
  }
}
