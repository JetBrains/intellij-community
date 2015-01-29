package org.jetbrains.debugger.values;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Obsolescent;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.EvaluateContext;
import org.jetbrains.debugger.Variable;
import org.jetbrains.debugger.VariablesHost;

import java.util.List;

/**
 * A compound value that has zero or more properties
 */
public interface ObjectValue extends Value {
  @Nullable
  String getClassName();

  @NotNull
  Promise<List<Variable>> getProperties();

  @NotNull
  Promise<List<Variable>> getProperties(@NotNull List<String> names, @NotNull EvaluateContext evaluateContext, @NotNull Obsolescent obsolescent);

  @NotNull
  VariablesHost getVariablesHost();

  /**
   * from (inclusive) to (exclusive) ranges of array elements or elements if less than bucketThreshold
   *
   * "to" could be -1 (sometimes length is unknown, so, you can pass -1 instead of actual elements size)
   */
  @NotNull
  Promise<Void> getIndexedProperties(int from, int to, int bucketThreshold, @NotNull IndexedVariablesConsumer consumer, @Nullable ValueType componentType);

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
}