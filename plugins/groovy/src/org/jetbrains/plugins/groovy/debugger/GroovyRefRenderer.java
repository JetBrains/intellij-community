// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
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
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.concurrent.CompletableFuture;

/**
 * @author peter
 */
public class GroovyRefRenderer extends NodeRendererImpl {
  private static final Key<NodeRenderer> GROOVY_REF_DELEGATE_RENDERER = new Key<>("GROOVY_REF_DELEGATE_RENDERER");

  public GroovyRefRenderer() {
    super("Groovy Reference", true);
    setIsApplicableChecker(type -> type instanceof ReferenceType
                                   ? DebuggerUtilsAsync.instanceOf(type, GroovyCommonClassNames.GROOVY_LANG_REFERENCE)
                                   : CompletableFuture.completedFuture(false));
  }

  @Override
  public String getUniqueId() {
    return "GroovyRefRenderer";
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(value, evaluationContext.getProject());
    getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor)
      .thenAccept(renderer -> {
        builder.getParentDescriptor().putUserData(GROOVY_REF_DELEGATE_RENDERER, renderer);
        renderer.buildChildren(fieldDescriptor.getValue(), builder, evaluationContext);
      });
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    NodeRenderer renderer = node.getParent().getDescriptor().getUserData(GROOVY_REF_DELEGATE_RENDERER);
    return renderer != null ? renderer.getChildValueExpression(node, context) : null;
  }

  @Override
  public CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(value, evaluationContext.getProject());
    return getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor)
      .thenCompose(renderer -> renderer.isExpandableAsync(fieldDescriptor.getValue(), evaluationContext, fieldDescriptor));
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(descriptor.getValue(), evaluationContext.getProject(), descriptor);
    CompletableFuture<NodeRenderer> renderer = getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor);
    return calcLabel(renderer, fieldDescriptor, evaluationContext, listener);
  }

  private static CompletableFuture<NodeRenderer> getDelegateRenderer(DebugProcess debugProcess, ValueDescriptor fieldDescriptor) {
    return ((DebugProcessImpl)debugProcess).getAutoRendererAsync(fieldDescriptor.getType());
  }

  private static ValueDescriptor getWrappedDescriptor(Value ref, final Project project) {
    return getWrappedDescriptor(ref, project, null);
  }

  private static ValueDescriptor getWrappedDescriptor(Value ref, final Project project, @Nullable ValueDescriptor originalDescriptor) {
    final Field field = ((ObjectReference)ref).referenceType().fieldByName("value");
    final Value wrapped = ((ObjectReference)ref).getValue(field);
    return new ValueDescriptorImpl(project, wrapped) {

      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
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
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) {
        return JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText("this." + field.name(), null);
      }
    };
  }

  @Override
  @Nullable
  public String calcIdLabel(ValueDescriptor descriptor, DebugProcess process, DescriptorLabelListener labelListener) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(descriptor.getValue(), process.getProject());
    return getDelegateRenderer(process, fieldDescriptor)
      .thenApply(renderer -> ((NodeRendererImpl)renderer).calcIdLabel(fieldDescriptor, process, labelListener))
      .getNow("");
  }
}
