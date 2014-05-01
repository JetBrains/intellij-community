package org.jetbrains.debugger.values;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Ranges of array elements or elements if less than bucketThreshold
   */
  @Nullable
  ActionCallback getVariables(int from, int to, int bucketThreshold, @NotNull IndexedVariablesConsumer consumer);
}