package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.Consumer;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScopeVariablesGroup extends XValueGroup {
  private final Scope scope;
  private final VariableContext context;

  private final CallFrame callFrame;

  public ScopeVariablesGroup(@NotNull final Scope scope, @NotNull final VariableContext context, @Nullable CallFrame callFrame) {
    super(createScopeNodeName(scope));

    this.scope = scope;

    if (callFrame == null || scope.getType() == Scope.Type.LIBRARY) {
      // functions scopes - we can watch variables only from global scope
      this.context = new ParentlessVariableContext(context, scope, scope.getType() == Scope.Type.GLOBAL);
    }
    else {
      this.context = new ScopedVariableContext(context, scope);
    }

    this.callFrame = scope.getType() == Scope.Type.LOCAL ? callFrame : null;
  }

  public static void createAndAddScopeList(@NotNull XCompositeNode node, @NotNull List<Scope> scopes, @NotNull VariableContext context, @Nullable CallFrame callFrame) {
    XValueChildrenList list = new XValueChildrenList(scopes.size());
    for (Scope scope : scopes) {
      list.addTopGroup(new ScopeVariablesGroup(scope, context, callFrame));
    }
    node.addChildren(list, true);
  }

  private static String createScopeNodeName(@NotNull Scope scope) {
    switch (scope.getType()) {
      case GLOBAL:
        return XDebuggerBundle.message("scope.global");
      case LOCAL:
        return XDebuggerBundle.message("scope.local");
      case WITH:
        return XDebuggerBundle.message("scope.with");
      case CLOSURE:
        return XDebuggerBundle.message("scope.closure");
      case CATCH:
        return XDebuggerBundle.message("scope.catch");
      case LIBRARY:
        return XDebuggerBundle.message("scope.library");
      case INSTANCE:
        return XDebuggerBundle.message("scope.instance");
      case CLASS:
        return XDebuggerBundle.message("scope.class");
      case UNKNOWN:
        return XDebuggerBundle.message("scope.unknown");
      default:
        throw new IllegalArgumentException(scope.getType().name());
    }
  }

  @Override
  public boolean isAutoExpand() {
    return scope.getType() == Scope.Type.LOCAL || scope.getType() == Scope.Type.CATCH;
  }

  @Nullable
  @Override
  public String getComment() {
    String className = scope.getDescription();
    return "Object".equals(className) ? null : className;
  }

  @Override
  public void computeChildren(final @NotNull XCompositeNode node) {
    ActionCallback callback = callFrame == null ? null : new ActionCallback().doWhenDone(new Runnable() {
      @Override
      public void run() {
        if (node.isObsolete()) {
          return;
        }

        callFrame.getReceiverVariable().doWhenDone(new Consumer<Variable>() {
          @Override
          public void consume(Variable variable) {
            if (!node.isObsolete()) {
              node.addChildren(variable == null ? XValueChildrenList.EMPTY : XValueChildrenList.singleton(CallFrameBase.RECEIVER_NAME, new VariableView(context, variable)), true);
            }
          }
        }).doWhenRejected(new Consumer<String>() {
          @Override
          public void consume(@Nullable String error) {
            if (!node.isObsolete()) {
              node.addChildren(XValueChildrenList.EMPTY, true);
            }
          }
        });
      }
    });
    Variables.processScopeVariables(scope, node, context, callback);
  }

  private static class ScopedVariableContext implements VariableContext {
    private final VariableContext parentContext;
    private final Scope scope;

    public ScopedVariableContext(@NotNull VariableContext parentContext, @NotNull Scope scope) {
      this.parentContext = parentContext;
      this.scope = scope;
    }

    @Nullable
    @Override
    public String getName() {
      return parentContext.getName();
    }

    @NotNull
    @Override
    public MemberFilter getMemberFilter() {
      return parentContext.getMemberFilter();
    }

    @NotNull
    @Override
    public EvaluateContext getEvaluateContext() {
      return parentContext.getEvaluateContext();
    }

    @NotNull
    @Override
    public DebuggerViewSupport getDebugProcess() {
      return parentContext.getDebugProcess();
    }

    @Override
    public boolean watchableAsEvaluationExpression() {
      return parentContext.watchableAsEvaluationExpression();
    }

    @Nullable
    @Override
    public Scope getScope() {
      return scope;
    }

    @Nullable
    @Override
    public VariableContext getParent() {
      return parentContext;
    }
  }

  private static final class ParentlessVariableContext extends ScopedVariableContext {
    private final boolean watchableAsEvaluationExpression;

    public ParentlessVariableContext(@NotNull VariableContext parentContext, @NotNull Scope scope, boolean watchableAsEvaluationExpression) {
      super(parentContext, scope);

      this.watchableAsEvaluationExpression = watchableAsEvaluationExpression;
    }

    @Override
    public boolean watchableAsEvaluationExpression() {
      return watchableAsEvaluationExpression;
    }

    @Nullable
    @Override
    public VariableContext getParent() {
      return null;
    }
  }
}