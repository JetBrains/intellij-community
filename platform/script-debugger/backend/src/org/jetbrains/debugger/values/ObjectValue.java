package org.jetbrains.debugger.values;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.Variable;

import java.util.List;

/**
 * A compound value that has zero or more properties
 */
public interface ObjectValue extends Value {
  void clearCaches();

  @Nullable
  String getClassName();

  @NotNull
  AsyncResult<List<Variable>> getProperties();

  /**
   * from (inclusive) to (exclusive) ranges of array elements or elements if less than bucketThreshold
   *
   * "to" could be -1 (sometimes length is unknown, so, you can pass -1 instead of actual elements size)
   */
  @NotNull
  ActionCallback getIndexedProperties(int from, int to, int bucketThreshold, @NotNull IndexedVariablesConsumer consumer, @Nullable ValueType componentType);

  /**
   * It must return quickly. Return {@link com.intellij.util.ThreeState#UNSURE} otherwise.
   */
  @NotNull
  ThreeState hasProperties();

  /**
   * It must return quickly. Return {@link com.intellij.util.ThreeState#UNSURE} otherwise.
   */
  @NotNull
  ThreeState hasIndexedProperties();

  int getCacheStamp();
}