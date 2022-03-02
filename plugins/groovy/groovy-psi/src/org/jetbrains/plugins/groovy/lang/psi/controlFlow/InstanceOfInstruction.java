// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionList;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.VariableDescriptorFactory;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.InferenceKt;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.isThisRef;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isNullLiteral;

/**
 * @author peter
 */
public class InstanceOfInstruction extends InstructionImpl implements MixinTypeInstruction {
  private final ConditionInstruction myCondition;
  private final Map<VariableDescriptor, Integer> myVariableIndex;

  public InstanceOfInstruction(@NotNull GroovyPsiElement assertion,
                               ConditionInstruction cond,
                               Map<VariableDescriptor, Integer> index) {
    super(assertion);
    myCondition = cond;
    myVariableIndex = index;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return Objects.requireNonNull(super.getElement());
  }

  @NotNull
  @Override
  protected String getElementPresentation() {
    return "instanceof: " + getElement().getText();
  }

  @Nullable
  // todo: lazy
  private Pair<GrExpression, PsiType> getInstanceof() {
    final PsiElement element = getElement();
    if (element instanceof GrInstanceOfExpression) {
      GrExpression operand = ((GrInstanceOfExpression)element).getOperand();
      final GrTypeElement typeElement = ((GrInstanceOfExpression)element).getTypeElement();
      if (operand instanceof GrReferenceExpression) {
        GrExpression qualifier = ((GrReferenceExpression)operand).getQualifier();
        if ((qualifier == null || isThisRef(qualifier)) && typeElement != null) {
          return Pair.create(((GrInstanceOfExpression)element).getOperand(), typeElement.getType());
        }
      }
    }
    else if (element instanceof GrBinaryExpression && ControlFlowBuilderUtil.isInstanceOfBinary((GrBinaryExpression)element)) {
      GrExpression left = ((GrBinaryExpression)element).getLeftOperand();
      GrExpression right = ((GrBinaryExpression)element).getRightOperand();
      if (right == null) return null;
      GroovyResolveResult result = ((GrReferenceExpression)right).advancedResolve();
      final PsiElement resolved = result.getElement();
      if (resolved instanceof PsiClass) {
        PsiClassType type = JavaPsiFacade.getElementFactory(element.getProject()).createType((PsiClass)resolved, result.getSubstitutor());
        return new Pair<>(left, type);
      }
    }
    else if (element instanceof GrBinaryExpression) {
      GrExpression left = ((GrBinaryExpression)element).getLeftOperand();
      GrExpression right = ((GrBinaryExpression)element).getRightOperand();
      if (isNullLiteral(right)) {
        return Pair.create(left, PsiType.NULL);
      }
      else if (right != null && isNullLiteral(left)) {
        return Pair.create(right, PsiType.NULL);
      }
    } else if (element instanceof GrExpressionList && element.getParent() instanceof GrCaseSection && element.getParent().getParent() instanceof GrSwitchElement) {
      // this branch corresponds to an arm of switch expression that is of a kind 'case Integer, String, Foo -> ...'
      var switchElement = (GrSwitchElement)element.getParent().getParent();
      GrCondition condition = switchElement.getCondition();
      if (condition instanceof GrReferenceExpression) {
        List<GrExpression> expressions = PsiUtil.getAllPatternsForCaseSection((GrCaseSection)element.getParent());
        List<PsiClass> patternClasses = ContainerUtil.mapNotNull(expressions, (GrExpression expr) -> {
          if (expr instanceof GrReferenceExpression) {
            var resolved = ((GrReferenceExpression)expr).resolve();
            if (resolved instanceof PsiClass) {
              return (PsiClass)resolved;
            }
          }
          return null;
        });
        if (patternClasses.size() != expressions.size()) {
          // something weird is in the case arm
          return null;
        }
        var classTypes = ContainerUtil.map(patternClasses, InferenceKt::type);
        PsiType commonType = TypesUtil.getLeastUpperBoundNullable(classTypes, element.getManager());
        return Pair.create((GrReferenceExpression)condition, commonType);
      }
    }
    return null;
  }

  @Override
  @Nullable
  public PsiType inferMixinType() {
    Pair<GrExpression, PsiType> instanceOf = getInstanceof();
    if (instanceOf == null) return null;

    return instanceOf.getSecond();
  }

  @Nullable
  @Override
  public ReadWriteVariableInstruction getInstructionToMixin(Instruction[] flow) {
    Pair<GrExpression, PsiType> instanceOf = getInstanceof();
    if (instanceOf == null) return null;

    Instruction instruction = ControlFlowUtils.findInstruction(instanceOf.getFirst(), flow);
    if (instruction instanceof ReadWriteVariableInstruction) {
      return (ReadWriteVariableInstruction)instruction;
    }
    return null;
  }

  @Override
  public int getVariableDescriptor() {
    Pair<GrExpression, PsiType> instanceOf = getInstanceof();
    if (instanceOf == null || !(instanceOf.first instanceof GrReferenceExpression)) return 0;
    VariableDescriptor descriptor = VariableDescriptorFactory.createDescriptor((GrReferenceExpression)instanceOf.first);
    if (descriptor == null) {
      return 0;
    }
    Integer descr = myVariableIndex.get(descriptor);
    return descr == null ? 0 : descr;
  }

  @Nullable
  @Override
  public ConditionInstruction getConditionInstruction() {
    return myCondition;
  }
}
