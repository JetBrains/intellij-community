package org.jetbrains.debugger;

import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.Promise;

import java.util.List;

public class ScopeVariablesGroup extends XValueGroup {
  private final Scope scope;
  private final VariableContext context;

  private final CallFrame callFrame;

  public ScopeVariablesGroup(@NotNull Scope scope, @NotNull VariableContext parentContext, @Nullable CallFrame callFrame) {
    super(createScopeNodeName(scope));

    this.scope = scope;
    context = createVariableContext(scope, parentContext, callFrame);
    this.callFrame = scope.getType() == Scope.Type.LOCAL ? callFrame : null;
  }

  // public only for tests
  @NotNull
  public static VariableContext createVariableContext(@NotNull Scope scope, @NotNull VariableContext parentContext, @Nullable CallFrame callFrame) {
    if (callFrame == null || scope.getType() == Scope.Type.LIBRARY) {
      // functions scopes - we can watch variables only from global scope
      return new ParentlessVariableContext(parentContext, scope, scope.getType() == Scope.Type.GLOBAL);
    }
    else {
      return new VariableContextWrapper(parentContext, scope);
    }
  }

  @TestOnly
  @NotNull
  public Scope getScope() {
    return scope;
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
    Promise<Void> promise = Variables.processScopeVariables(scope, node, context, callFrame == null);
    if (callFrame != null) {
      promise.done(new ObsolescentConsumer<Void>(node) {
        @Override
        public void consume(Void ignored) {
          callFrame.getReceiverVariable()
            .done(new ObsolescentConsumer<Variable>(node) {
              @Override
              public void consume(Variable variable) {
                node.addChildren(variable == null ? XValueChildrenList.EMPTY : XValueChildrenList.singleton(new VariableView(variable, context)), true);
              }
            })
            .rejected(new ObsolescentConsumer<Throwable>(node) {
              @Override
              public void consume(@Nullable Throwable error) {
                node.addChildren(XValueChildrenList.EMPTY, true);
              }
            });
        }
      });
    }
  }

  private static final class ParentlessVariableContext extends VariableContextWrapper {
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