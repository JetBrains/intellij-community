package org.jetbrains.debugger;

import com.intellij.util.ThreeState;
import org.jetbrains.debugger.values.Value;

/**
 * A JavaScript exception data holder for exceptions reported by a JavaScript
 * virtual machine.
 */
public interface ExceptionData {
  /**
   * @return the thrown exception value
   */
  Value getExceptionValue();

  /**
   * @return whether this exception is uncaught
   */
  ThreeState isUncaught();

  /**
   * @return the text of the source line where the exception was thrown or null
   */
  String getSourceText();

  /**
   * @return the exception description (plain text)
   */
  String getExceptionMessage();
}