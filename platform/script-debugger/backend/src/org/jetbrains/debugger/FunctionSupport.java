package org.jetbrains.debugger;

/**
 * Supports targets that refer to function text in form of function-returning
 * JavaScript expression.
 * E.g. you can set a breakpoint on the 5th line of user method addressed as
 * 'PropertiesDialog.prototype.loadData'.
 * Expression is calculated immediately and never recalculated again.
 */
public interface FunctionSupport {
  /**
   * @return not null
   */
  BreakpointTarget createTarget(String expression);

  /**
   * Additional interface that user visitor may implement for {@link BreakpointTarget#accept}
   * method.
   */
  interface Visitor<R> extends BreakpointTarget.Visitor<R> {
    R visitFunction(String expression);
  }
}