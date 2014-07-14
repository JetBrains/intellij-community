package org.jetbrains.debugger;

/**
 * Supports targets that refer to a script by a 'regexp' of its name.
 * After {@link Vm#setBreakpoint} is
 * called, breakpoint will be set on every script currently loaded in VM whose name matches.
 * E.g. you can safely set a breakpoint before the script is actually loaded.
 */
public interface ScriptRegExpSupport {
  /**
   * @param regExp JavaScript RegExp
   * @return not null
   */
  BreakpointTarget createTarget(String regExp);

  /**
   * Additional interface that user visitor may implement for {@link BreakpointTarget#accept}
   * method.
   */
  interface Visitor<R> extends BreakpointTarget.Visitor<R> {
    /**
     * @param regExp regular expression pattern (as specified in JavaScript) that will be
     *     used to match script names
     */
    R visitRegExp(String regExp);
  }
}