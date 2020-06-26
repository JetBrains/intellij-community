// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class CatchMayIgnoreExceptionInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final String IGNORED_PARAMETER_NAME = "ignored";

  public boolean m_ignoreCatchBlocksWithComments = true;
  public boolean m_ignoreNonEmptyCatchBlock = true;
  public boolean m_ignoreUsedIgnoredName = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.comments"),
                      "m_ignoreCatchBlocksWithComments");
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.nonempty"), "m_ignoreNonEmptyCatchBlock");
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.ignored.used"), "m_ignoreUsedIgnoredName");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTryStatement(PsiTryStatement statement) {
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
        if (PsiUtil.isIgnoredName(parameterName)) {
          if (!m_ignoreUsedIgnoredName && VariableAccessUtils.variableIsUsed(parameter, section)) {
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
          RenameCatchParameterFix renameFix = new RenameCatchParameterFix(generateName(block));
          AddCatchBodyFix addBodyFix = getAddBodyFix(block);
          holder.registerProblem(catchToken, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.empty.message"),
                                 renameFix, addBodyFix, fix);
        }
        else if (!VariableAccessUtils.variableIsUsed(parameter, section)) {
          if (!m_ignoreNonEmptyCatchBlock &&
              (!m_ignoreCatchBlocksWithComments || PsiTreeUtil.getChildOfType(block, PsiComment.class) == null)) {
            holder.registerProblem(identifier, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.unused.message"),
                                   new RenameFix(generateName(block), false, false), fix);
          }
        }
        else if (mayIgnoreVMException(parameter, block)) {
          holder.registerProblem(catchToken, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.vm.ignored.message"), fix);
        }
      }

      @Nullable
      private AddCatchBodyFix getAddBodyFix(PsiCodeBlock block) {
        if (ControlFlowUtils.isEmpty(block, true, true)) {
          try {
            FileTemplate template =
              FileTemplateManager.getInstance(holder.getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
            if (!StringUtil.isEmptyOrSpaces(template.getText())) {
              return new AddCatchBodyFix();
            }
          }
          catch (IllegalStateException ignored) { }
        }
        return null;
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

        class CatchDataFlowRunner extends DataFlowRunner {
          final DfaVariableValue myExceptionVar;
          final DfaVariableValue myStableExceptionVar;

          CatchDataFlowRunner() {
            super(holder.getProject(), block);
            DfaValueFactory factory = getFactory();
            myExceptionVar = factory.getVarFactory().createVariableValue(parameter);
            myStableExceptionVar = factory.getVarFactory().createVariableValue(new LightParameter("tmp", exception, block));
          }

          @NotNull
          @Override
          protected List<DfaInstructionState> createInitialInstructionStates(@NotNull PsiElement psiBlock,
                                                                             @NotNull Collection<? extends DfaMemoryState> memStates,
                                                                             @NotNull ControlFlow flow) {
            DfaValueFactory factory = getFactory();

            for (DfaMemoryState memState : memStates) {
              memState.applyCondition(myExceptionVar.eq(myStableExceptionVar));
              memState.applyCondition(
                myExceptionVar.cond(RelationType.IS, factory.getObjectType(exception, Nullability.NOT_NULL)));
            }
            return super.createInitialInstructionStates(psiBlock, memStates, flow);
          }
        }

        CatchDataFlowRunner runner = new CatchDataFlowRunner();
        StandardInstructionVisitor visitor = new IgnoredExceptionVisitor(parameter, block, exceptionClass, runner.myStableExceptionVar);
        return runner.analyzeCodeBlock(block, visitor) == RunnerResult.OK;
      }
    };
  }

  @NotNull
  private static String generateName(PsiCodeBlock block) {
    return new VariableNameGenerator(block, VariableKind.LOCAL_VARIABLE).byName(IGNORED_PARAMETER_NAME).generate(true);
  }

  static class IgnoredExceptionVisitor extends SideEffectVisitor {
    private final @NotNull PsiParameter myParameter;
    private final @NotNull PsiCodeBlock myBlock;
    private final @NotNull List<PsiMethod> myMethods;
    private final @NotNull DfaVariableValue myExceptionVar;

    IgnoredExceptionVisitor(@NotNull PsiParameter parameter,
                                   @NotNull PsiCodeBlock block,
                                   @NotNull PsiClass exceptionClass,
                                   @NotNull DfaVariableValue exceptionVar) {
      myParameter = parameter;
      myBlock = block;
      myExceptionVar = exceptionVar;
      myMethods = StreamEx.of("getMessage", "getLocalizedMessage", "getCause")
        .flatArray(name -> exceptionClass.findMethodsByName(name, true))
        .filter(m -> m.getParameterList().isEmpty())
        .toList();
    }

    @Override
    public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myMethods.contains(instruction.getTargetMethod())) {
        DfaValue qualifier = memState.peek();
        // Methods like "getCause" and "getMessage" return "null" for our test exception
        if (memState.areEqual(qualifier, myExceptionVar)) {
          memState.pop();
          memState.push(runner.getFactory().getNull());
          return nextInstruction(instruction, runner, memState);
        }
      }
      return super.visitMethodCall(instruction, runner, memState);
    }

    @Override
    protected boolean isModificationAllowed(DfaVariableValue variable) {
      PsiModifierListOwner owner = variable.getPsiVariable();
      return owner == myParameter || owner != null && PsiTreeUtil.isAncestor(myBlock, owner, false);
    }
  }

  private static class AddCatchBodyFix implements LocalQuickFix, LowPriorityAction {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.empty.catch.block.generate.body");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiCatchSection catchSection = ObjectUtils.tryCast(descriptor.getPsiElement().getParent(), PsiCatchSection.class);
      if (catchSection == null) return;
      PsiParameter parameter = catchSection.getParameter();
      if (parameter == null) return;
      String parameterName = parameter.getName();
      FileTemplate template = FileTemplateManager.getInstance(project).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);

      Properties props = FileTemplateManager.getInstance(project).getDefaultProperties();
      props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, parameterName);
      props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION_TYPE, parameter.getType().getCanonicalText());
      PsiDirectory directory = catchSection.getContainingFile().getContainingDirectory();
      if (directory != null) {
        JavaTemplateUtil.setPackageNameAttribute(props, directory);
      }

      try {
        PsiCodeBlock block =
          PsiElementFactory.getInstance(project).createCodeBlockFromText("{\n" + template.getText(props) + "\n}", null);
        Objects.requireNonNull(catchSection.getCatchBlock()).replace(block);
      }
      catch (ProcessCanceledException ce) {
        throw ce;
      }
      catch (Exception e) {
        throw new IncorrectOperationException("Incorrect file template", (Throwable)e);
      }
    }
  }

  private static final class RenameCatchParameterFix implements LocalQuickFix {
    private final String myName;

    private RenameCatchParameterFix(String name) {
      myName = name;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("rename.catch.parameter.to.ignored", myName);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("rename.catch.parameter.to.ignored", IGNORED_PARAMETER_NAME);
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
      final PsiIdentifier newIdentifier = factory.createIdentifier(myName);
      identifier.replace(newIdentifier);
    }
  }
}
