package org.jetbrains.debugger.values;

public interface ArrayValue extends ObjectValue {
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
}