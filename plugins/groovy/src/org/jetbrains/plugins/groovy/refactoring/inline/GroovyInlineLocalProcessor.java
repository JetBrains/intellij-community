// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.BaseUsageViewDescriptor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyInlineLocalProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(GroovyInlineLocalProcessor.class);

  private final InlineLocalVarSettings mySettings;
  private final GrVariable myLocal;

  public GroovyInlineLocalProcessor(Project project, InlineLocalVarSettings settings, GrVariable local) {
    super(project);
    this.mySettings = settings;
    this.myLocal = local;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new BaseUsageViewDescriptor(myLocal);
  }


  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final UsageInfo[] usages = refUsages.get();
    for (UsageInfo usage : usages) {
      collectConflicts(usage.getReference(), conflicts);
    }

    return showConflicts(conflicts, usages);
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof ClosureUsage) return true;
    }
    return false;
  }

  private void collectConflicts(final PsiReference reference, final MultiMap<PsiElement, String> conflicts) {
    GrExpression expr = (GrExpression)reference.getElement();
    if (PsiUtil.isAccessedForWriting(expr)) {
      conflicts.putValue(expr, GroovyRefactoringBundle.message("variable.is.accessed.for.writing", myLocal.getName()));
    }
  }


  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final GroovyControlFlow controlFlow = mySettings.getFlow();
    final List<BitSet> writes = ControlFlowUtils.inferWriteAccessMap(controlFlow, myLocal);

    ArrayList<UsageInfo> toInline = new ArrayList<>();
    collectRefs(myLocal, controlFlow.getFlow(), writes, mySettings.getWriteInstructionNumber(), toInline);

    return toInline.toArray(UsageInfo.EMPTY_ARRAY);
  }

  /**
   * ClosureUsage represents usage of local var inside closure
   */
  private static final class ClosureUsage extends UsageInfo {
    private ClosureUsage(@NotNull PsiReference reference) {
      super(reference);
    }
  }

  private static void collectRefs(final GrVariable variable,
                                  Instruction[] flow,
                                  final List<BitSet> writes,
                                  final int writeInstructionNumber,
                                  final ArrayList<UsageInfo> toInline) {
    for (Instruction instruction : flow) {
      final PsiElement element = instruction.getElement();
      if (instruction instanceof ReadWriteVariableInstruction) {
        if (((ReadWriteVariableInstruction)instruction).isWrite()) continue;

        if (element instanceof GrVariable && element != variable) continue;
        if (!(element instanceof GrReferenceExpression ref)) continue;

        if (ref.isQualified() || ref.resolve() != variable) continue;

        final BitSet prev = writes.get(instruction.num());
        if (writeInstructionNumber >= 0 && prev.cardinality() == 1 && prev.get(writeInstructionNumber)) {
          toInline.add(new UsageInfo(ref));
        }
        else if (writeInstructionNumber == -1 && prev.cardinality() == 0) {
          toInline.add(new ClosureUsage(ref));
        }
      }
      else if (element instanceof GrClosableBlock) {
        final BitSet prev = writes.get(instruction.num());
        if (writeInstructionNumber >= 0 && prev.cardinality() == 1 && prev.get(writeInstructionNumber) ||
            writeInstructionNumber == -1 && prev.cardinality() == 0) {
          final GroovyControlFlow closureFlow = ControlFlowUtils.getGroovyControlFlow((GrClosableBlock)element);
          collectRefs(variable, closureFlow.getFlow(), ControlFlowUtils.inferWriteAccessMap(closureFlow, variable), -1, toInline);
        }
      }
      else if (element instanceof GrAnonymousClassDefinition) {
        final BitSet prev = writes.get(instruction.num());
        if (writeInstructionNumber >= 0 && prev.cardinality() == 1 && prev.get(writeInstructionNumber) ||
            writeInstructionNumber == -1 && prev.cardinality() == 0) {
          ((GrAnonymousClassDefinition)element).acceptChildren(new GroovyRecursiveElementVisitor() {
            @Override
            public void visitField(@NotNull GrField field) {
              GrExpression initializer = field.getInitializerGroovy();
              if (initializer != null) {
                GroovyControlFlow flow = ControlFlowBuilder.buildControlFlow(initializer);
                collectRefs(variable, flow.getFlow(), ControlFlowUtils.inferWriteAccessMap(flow, variable), -1, toInline);
              }
            }

            @Override
            public void visitMethod(@NotNull GrMethod method) {
              GrOpenBlock block = method.getBlock();
              if (block != null) {
                GroovyControlFlow flow = ControlFlowUtils.getGroovyControlFlow(block);
                collectRefs(variable, flow.getFlow(), ControlFlowUtils.inferWriteAccessMap(flow, variable), -1, toInline);
              }
            }

            @Override
            public void visitClassInitializer(@NotNull GrClassInitializer initializer) {
              GrOpenBlock block = initializer.getBlock();
              GroovyControlFlow flow = ControlFlowUtils.getGroovyControlFlow(block);
              collectRefs(variable, flow.getFlow(), ControlFlowUtils.inferWriteAccessMap(flow, variable), -1, toInline);
            }
          });
        }
      }
    }
  }


  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);

    final GrExpression initializer = mySettings.getInitializer();

    GrExpression initializerToUse = GrIntroduceHandlerBase.insertExplicitCastIfNeeded(myLocal, mySettings.getInitializer());

    for (UsageInfo usage : usages) {
      GrVariableInliner.inlineReference(usage, myLocal, initializerToUse);
    }

    final PsiElement initializerParent = initializer.getParent();

    if (initializerParent instanceof GrAssignmentExpression) {
      initializerParent.delete();
      return;
    }

    if (initializerParent instanceof GrVariable) {
      final Collection<PsiReference> all = ReferencesSearch.search(myLocal).findAll();
      if (!all.isEmpty()) {
        initializer.delete();
        return;
      }
    }

    final PsiElement owner = myLocal.getParent().getParent();
    if (owner instanceof GrVariableDeclarationOwner) {
      ((GrVariableDeclarationOwner)owner).removeVariable(myLocal);
    }
    else {
      myLocal.delete();
    }
  }


  @NotNull
  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("inline.command", myLocal.getName());
  }
}