package org.jetbrains.debugger.values

interface ArrayValue : ObjectValue {
  /**
   * Be aware - it is not equals to java array length.
   * In case of sparse array `var sparseArray = [3, 4];
   * sparseArray[45] = 34;
   * sparseArray[40999995] = &quot;foo&quot;;
  ` *
   * length will be equal to 40999995.
   */
  val length: Int
}