package org.jetbrains.debugger.values;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.Variable;

import java.util.List;

public interface ArrayValue extends Value {
  /**
   * Be aware - it is not equals to java array length.
   * In case of sparse array {@code
   * var sparseArray = [3, 4];
   * sparseArray[45] = 34;
   * sparseArray[40999995] = "foo";
   * }
   * length will be equal to 40999995.
   */
  int getLength();

  @NotNull
  AsyncResult<List<Variable>> getVariables();
}