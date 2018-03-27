// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

public class CatchMayIgnoreExceptionInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final String IGNORED_PARAMETER_NAME = "ignored";

  public boolean m_ignoreCatchBlocksWithComments = true;
  public boolean m_ignoreNonEmptyCatchBlock = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.comments"),
                      "m_ignoreCatchBlocksWithComments");
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.nonempty"), "m_ignoreNonEmptyCatchBlock");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTryStatement(@NotNull PsiTryStatement statement) {
        super.visitTryStatement(statement);
        final PsiCatchSection[] catchSections = statement.getCatchSections();
        for (final PsiCatchSection section : catchSections) {
          checkCatchSection(section);
        }
      }

      private void checkCatchSection(PsiCatchSection section) {
        final PsiParameter parameter = section.getParameter();
        if (parameter == null) return;
        final PsiIdentifier identifier = parameter.getNameIdentifier();
        if (identifier == null) return;
        final String parameterName = parameter.getName();
        if (parameterName == null) return;
        if (PsiUtil.isIgnoredName(parameterName)) {
          if (VariableAccessUtils.variableIsUsed(parameter, section)) {
            holder.registerProblem(identifier, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.used.message"));
          }
          return;
        }
        if ((parameterName.equals("expected") || parameterName.equals("ok")) && TestUtils.isInTestSourceContent(section)) {
          return;
        }
        final PsiElement catchToken = section.getFirstChild();
        if (catchToken == null) return;

        final PsiCodeBlock block = section.getCatchBlock();
        if (block == null) return;
        SuppressForTestsScopeFix fix = SuppressForTestsScopeFix.build(CatchMayIgnoreExceptionInspection.this, section);
        if (ControlFlowUtils.isEmpty(block, m_ignoreCatchBlocksWithComments, true)) {
          holder.registerProblem(catchToken, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.empty.message"),
                                 new EmptyCatchBlockFix(), fix);
        }
        else if (!VariableAccessUtils.variableIsUsed(parameter, section)) {
          if (!m_ignoreNonEmptyCatchBlock &&
              (!m_ignoreCatchBlocksWithComments || PsiTreeUtil.getChildOfType(block, PsiComment.class) == null)) {
            holder.registerProblem(identifier, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.unused.message"),
                                   new RenameFix("ignored", false, false), fix);
          }
        }
        else if (mayIgnoreVMException(parameter, block)) {
          holder.registerProblem(catchToken, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.vm.ignored.message"), fix);
        }
      }

      /**
       * Returns true if given catch block may ignore VM exception such as NullPointerException
       *
       * @param parameter a catch block parameter
       * @param block     a catch block body
       * @return true if it's determined that catch block may ignore VM exception
       */
      private boolean mayIgnoreVMException(PsiParameter parameter, PsiCodeBlock block) {
        PsiType type = parameter.getType();
        if (!type.equalsToText(CommonClassNames.JAVA_LANG_THROWABLE) &&
            !type.equalsToText(CommonClassNames.JAVA_LANG_EXCEPTION) &&
            !type.equalsToText(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION) &&
            !type.equalsToText(CommonClassNames.JAVA_LANG_ERROR)) {
          return false;
        }
        // Let's assume that exception is NPE or SOE with null cause and null message and see what happens during dataflow.
        // Will it produce any side-effect?
        String className = type.equalsToText(CommonClassNames.JAVA_LANG_ERROR) ? "java.lang.StackOverflowError"
                                                                               : CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION;
        PsiClassType exception = JavaPsiFacade.getElementFactory(parameter.getProject()).createTypeByFQClassName(
          className, parameter.getResolveScope());
        PsiClass exceptionClass = exception.resolve();
        if (exceptionClass == null) return false;

        DataFlowRunner runner = new StandardDataFlowRunner(false, false);
        DfaValueFactory factory = runner.getFactory();
        DfaVariableValue exceptionVar = factory.getVarFactory().createVariableValue(parameter, false);
        DfaVariableValue stableExceptionVar =
          factory.getVarFactory().createVariableValue(new LightParameter("tmp", exception, block), false);

        StandardInstructionVisitor visitor = new IgnoredExceptionVisitor(parameter, block, exceptionClass, stableExceptionVar);
        Consumer<DfaMemoryState> stateAdjuster = state -> {
          state.applyCondition(factory.createCondition(exceptionVar, RelationType.EQ, stableExceptionVar));
          state
            .applyCondition(factory.createCondition(exceptionVar, RelationType.IS, factory.createTypeValue(exception, Nullness.NOT_NULL)));
          };
        return runner.analyzeCodeBlock(block, visitor, stateAdjuster) == RunnerResult.OK;
      }
    };
  }

  static class IgnoredExceptionVisitor extends SideEffectVisitor {
    private final @NotNull PsiParameter myParameter;
    private final @NotNull PsiCodeBlock myBlock;
    private final @NotNull List<PsiMethod> myMethods;
    private final @NotNull DfaVariableValue myExceptionVar;

    public IgnoredExceptionVisitor(@NotNull PsiParameter parameter,
                                   @NotNull PsiCodeBlock block,
                                   @NotNull PsiClass exceptionClass,
                                   @NotNull DfaVariableValue exceptionVar) {
      myParameter = parameter;
      myBlock = block;
      myExceptionVar = exceptionVar;
      myMethods = StreamEx.of("getMessage", "getLocalizedMessage", "getCause")
        .flatArray(name -> exceptionClass.findMethodsByName(name, true))
        .filter(m -> m.getParameterList().getParametersCount() == 0)
        .toList();
    }

    @Override
    public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myMethods.contains(instruction.getTargetMethod())) {
        DfaValue qualifier = memState.peek();
        DfaMemoryState copy = memState.createCopy();
        // Methods like "getCause" and "getMessage" return "null" for our test exception
        if (!copy.applyCondition(runner.getFactory().createCondition(qualifier, RelationType.NE, myExceptionVar))) {
          memState.pop();
          memState.push(runner.getFactory().getConstFactory().getNull());
          return nextInstruction(instruction, runner, memState);
        }
      }
      return super.visitMethodCall(instruction, runner, memState);
    }

    protected boolean isModificationAllowed(DfaVariableValue variable) {
      PsiModifierListOwner owner = variable.getPsiVariable();
      return owner == myParameter || PsiTreeUtil.isAncestor(myBlock, owner, false);
    }
  }

  private static class EmptyCatchBlockFix implements LocalQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("rename.catch.parameter.to.ignored");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiCatchSection)) return;
      final PsiCatchSection catchSection = (PsiCatchSection)parent;
      final PsiParameter parameter = catchSection.getParameter();
      if (parameter == null) return;
      final PsiIdentifier identifier = parameter.getNameIdentifier();
      if (identifier == null) return;
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiIdentifier newIdentifier = factory.createIdentifier(IGNORED_PARAMETER_NAME);
      identifier.replace(newIdentifier);
    }
  }
}
