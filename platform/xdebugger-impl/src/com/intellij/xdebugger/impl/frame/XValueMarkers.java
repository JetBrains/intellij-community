/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class XValueMarkers<V extends XValue, M> {
  private final XValueMarkerProvider<V, M> myProvider;
  private final Map<M, ValueMarkup> myMarkers;

  private XValueMarkers(@NotNull XValueMarkerProvider<V, M> provider) {
    myProvider = provider;
    myMarkers = new HashMap<M, ValueMarkup>();
  }

  public static <V extends XValue, M> XValueMarkers<V, M> createValueMarkers(@NotNull XValueMarkerProvider<V, M> provider) {
    return new XValueMarkers<V, M>(provider);
  }

  @Nullable
  public ValueMarkup getMarkup(@NotNull XValue value) {
    Class<V> valueClass = myProvider.getValueClass();
    if (!valueClass.isInstance(value)) return null;

    V v = valueClass.cast(value);
    if (!myProvider.canMark(v)) return null;

    M m = myProvider.getMarker(v);
    if (m == null) return null;

    return myMarkers.get(m);
  }

  public boolean canMarkValue(@NotNull XValue value) {
    Class<V> valueClass = myProvider.getValueClass();
    if (!valueClass.isInstance(value)) return false;

    return myProvider.canMark(valueClass.cast(value));
  }

  public void markValue(@NotNull XValue value, @NotNull ValueMarkup markup) {
    //noinspection unchecked
    M m = myProvider.markValue((V)value);
    myMarkers.put(m, markup);
  }

  public void unmarkValue(@NotNull XValue value) {
    //noinspection unchecked
    final V v = (V)value;
    M m = myProvider.getMarker(v);
    if (m != null) {
      myProvider.unmarkValue(v, m);
      myMarkers.remove(m);
    }
  }

  public Map<M, ValueMarkup> getAllMarkers() {
    return Collections.unmodifiableMap(myMarkers);
  }

  public void clear() {
    myMarkers.clear();
  }
}
