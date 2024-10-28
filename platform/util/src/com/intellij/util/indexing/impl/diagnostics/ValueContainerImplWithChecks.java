// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.diagnostics;

import com.intellij.util.SmartList;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * ValueContainer implementation with additional self-consistency checks.
 * Not for production use: to be used during test runs
 */
@ApiStatus.Internal
public class ValueContainerImplWithChecks<V> extends ValueContainerImpl<V> {

  private Int2ObjectMap<Object> presentInputIds = new Int2ObjectOpenHashMap<>();
  private List<UpdateOp> updateOps = new SmartList<>();

  public ValueContainerImplWithChecks() {
  }

  @Override
  protected @NotNull String getDebugMessage() {
    return "Actual value container = \n" + this +
           (presentInputIds == null ? "" : "\nExpected value container = " + presentInputIds) +
           (updateOps == null ? "" : "\nUpdate operations = " + updateOps);
  }

  @Override
  public ValueContainerImpl<V> clone() {
    ValueContainerImplWithChecks<V> clone = (ValueContainerImplWithChecks<V>)super.clone();
    clone.presentInputIds = new Int2ObjectOpenHashMap<>(presentInputIds);
    clone.updateOps = new SmartList<>(updateOps);
    return clone;
  }

  @Override
  protected void ensureInputIdIsntAssociatedWithAnotherValue(int inputId, Object value, boolean isDirect) {
    Object normalizedValue = wrapValue(value);
    Object previousValue = presentInputIds.put(inputId, normalizedValue);
    updateOps.add(new UpdateOp(isDirect ? UpdateOp.Type.ADD_DIRECT : UpdateOp.Type.ADD, inputId, normalizedValue));
    if (previousValue != null && !previousValue.equals(normalizedValue)) {
      LOG.error("Can't add value '" + normalizedValue + "'; input id " + inputId + " is already present in:\n" + getDebugMessage());
    }
  }

  protected void ensureInputIdAssociatedWithValue(int inputId, Object value) {
    Object normalizedValue = wrapValue(value);
    Object previousValue = presentInputIds.remove(inputId);
    updateOps.add(new UpdateOp(UpdateOp.Type.REMOVE, inputId, normalizedValue));
    if (previousValue != null && !previousValue.equals(normalizedValue)) {
      LOG.error("Can't remove value '" +
                normalizedValue +
                "'; input id " +
                inputId +
                " is not present for the specified value in:\n" +
                getDebugMessage());
    }
  }
}
