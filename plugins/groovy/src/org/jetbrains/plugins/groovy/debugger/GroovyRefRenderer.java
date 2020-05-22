// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.ChildrenBuilder;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.debugger.ui.tree.render.NodeRendererImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.concurrent.CompletableFuture;

/**
 * @author peter
 */
public class GroovyRefRenderer extends NodeRendererImpl {
  public GroovyRefRenderer() {
    super("Groovy Reference", true);
  }

  @Override
  public String getUniqueId() {
    return "GroovyRefRenderer";
  }

  @Override
  public boolean isApplicable(Type type) {
    return type instanceof ReferenceType && DebuggerUtils.instanceOf(type, GroovyCommonClassNames.GROOVY_LANG_REFERENCE);
  }

  @Override
  public CompletableFuture<Boolean> isApplicableAsync(Type type) {
    if (type instanceof ReferenceType) {
      return DebuggerUtilsAsync.instanceOf(type, GroovyCommonClassNames.GROOVY_LANG_REFERENCE);
    }
    return CompletableFuture.completedFuture(false);
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(value, evaluationContext.getProject());
    getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor).buildChildren(fieldDescriptor.getValue(), builder, evaluationContext);
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(((ValueDescriptor)node.getParent().getDescriptor()).getValue(),
                                                           context.getProject());
    return getDelegateRenderer(context.getDebugProcess(), fieldDescriptor).getChildValueExpression(node, context);
  }

  // TODO: make truly async
  @Override
  public CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(value, evaluationContext.getProject());
    return getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor)
      .isExpandableAsync(fieldDescriptor.getValue(), evaluationContext, fieldDescriptor);
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(descriptor.getValue(), evaluationContext.getProject(), descriptor);
    return getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor).calcLabel(fieldDescriptor, evaluationContext, listener);
  }

  private static NodeRenderer getDelegateRenderer(DebugProcess debugProcess, ValueDescriptor fieldDescriptor) {
    return ((DebugProcessImpl)debugProcess).getAutoRenderer(fieldDescriptor);
  }

  private static ValueDescriptor getWrappedDescriptor(Value ref, final Project project) {
    return getWrappedDescriptor(ref, project, null);
  }

  private static ValueDescriptor getWrappedDescriptor(Value ref, final Project project, @Nullable ValueDescriptor originalDescriptor) {
    final Field field = ((ObjectReference)ref).referenceType().fieldByName("value");
    final Value wrapped = ((ObjectReference)ref).getValue(field);
    return new ValueDescriptorImpl(project, wrapped) {

      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
        return wrapped;
      }

      @Override
      public void setValueLabel(@NotNull String label) {
        if (originalDescriptor != null) {
          originalDescriptor.setValueLabel(label);
        }
      }

      @Override
      public String calcValueName() {
        return field.name();
      }

      @Override
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
        return JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText("this." + field.name(), null);
      }
    };
  }

  // TODO: make truly async
  @Override
  public @Nullable String calcIdLabel(ValueDescriptor descriptor, DebugProcess process, DescriptorLabelListener labelListener) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(descriptor.getValue(), process.getProject());
    return ((NodeRendererImpl)getDelegateRenderer(process, fieldDescriptor)).calcIdLabel(fieldDescriptor, process, labelListener);
  }
}
