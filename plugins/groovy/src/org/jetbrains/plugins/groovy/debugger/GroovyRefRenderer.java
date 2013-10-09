package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
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
import com.intellij.psi.PsiExpression;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author peter
 */
public class GroovyRefRenderer extends NodeRendererImpl {
  public GroovyRefRenderer() {
    super("Groovy Reference");
    myProperties.setEnabled(true);
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
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(value, evaluationContext.getProject());
    getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor).buildChildren(fieldDescriptor.getValue(), builder, evaluationContext);
  }

  @Override
  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(((ValueDescriptor)node.getParent().getDescriptor()).getValue(),
                                                           context.getProject());
    return getDelegateRenderer(context.getDebugProcess(), fieldDescriptor).getChildValueExpression(node, context);
  }

  @Override
  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(value, evaluationContext.getProject());
    return getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor).isExpandable(fieldDescriptor.getValue(),
                                                                                                  evaluationContext, fieldDescriptor);
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(descriptor.getValue(), evaluationContext.getProject());
    return getDelegateRenderer(evaluationContext.getDebugProcess(), fieldDescriptor).calcLabel(fieldDescriptor, evaluationContext, listener);
  }

  private static NodeRenderer getDelegateRenderer(DebugProcess debugProcess, ValueDescriptor fieldDescriptor) {
    return ((DebugProcessImpl)debugProcess).getAutoRenderer(fieldDescriptor);
  }

  private static ValueDescriptor getWrappedDescriptor(Value ref, final Project project) {
    final Field field = ((ObjectReference)ref).referenceType().fieldByName("value");
    final Value wrapped = ((ObjectReference)ref).getValue(field);
    return new ValueDescriptorImpl(project, wrapped) {

      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
        return wrapped;
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

  @Nullable
  @Override
  public String getIdLabel(Value value, DebugProcess process) {
    ValueDescriptor fieldDescriptor = getWrappedDescriptor(value, process.getProject());
    return ((NodeRendererImpl)getDelegateRenderer(process, fieldDescriptor)).getIdLabel(fieldDescriptor.getValue(), process);
  }
}
