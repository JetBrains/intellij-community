package org.jetbrains.debugger;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

class VariableContextWrapper implements VariableContext {
  private final VariableContext parentContext;
  private final Scope scope;

  // it's worth to cache it (JavaScriptDebuggerViewSupport, for example, performs expensive computation)
  private final NotNullLazyValue<Promise<MemberFilter>> memberFilterPromise = new AtomicNotNullLazyValue<Promise<MemberFilter>>() {
    @NotNull
    @Override
    protected Promise<MemberFilter> compute() {
      return parentContext.getViewSupport().getMemberFilter(VariableContextWrapper.this);
    }
  };

  public VariableContextWrapper(@NotNull VariableContext parentContext, @Nullable Scope scope) {
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
  public Promise<MemberFilter> getMemberFilter() {
    return memberFilterPromise.getValue();
  }

  @NotNull
  @Override
  public EvaluateContext getEvaluateContext() {
    return parentContext.getEvaluateContext();
  }

  @NotNull
  @Override
  public DebuggerViewSupport getViewSupport() {
    return parentContext.getViewSupport();
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