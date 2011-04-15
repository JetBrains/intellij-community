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
package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

/**
 * Provides implementation of 'Mark Object' feature. <p>
 *
 * If debugger values have unique ids just return these ids from {@link #getMarker(XValue)} method.
 * Alternatively implement {@link #markValue(XValue)} to store a value in some registry and implement {@link #unmarkValue(XValue, Object)}
 * to remote it from the registry. In such a case the {@link #getMarker(XValue)} method can return {@code null} if the {@code value} isn't marked.
 *
 * @author nik
 */
public abstract class XValueMarkerProvider<V extends XValue, M> {
  private final Class<V> myValueClass;

  protected XValueMarkerProvider(Class<V> valueClass) {
    myValueClass = valueClass;
  }

  /**
   * @return {@code true} if 'Mark Object' action should be enabled for {@code value}
   */
  public abstract boolean canMark(@NotNull V value);

  /**
   * This method is used to determine whether the {@code value} was marked or not. The returned object is compared using {@link Object#equals(Object)}
   * method with markers returned by {@link #markValue(XValue)} methods. <p>
   * This method may return {@code null} if the {@code value} wasn't marked by {@link #markValue(XValue)} method.
   * @return a marker for {@code value}
   */
  public abstract M getMarker(@NotNull V value);

  /**
   * This method is called when 'Mark Object' action is invoked. Return an unique marker for {@code value} and store it in some registry
   * if necessary.
   * @return a marker for {@code value}
   */
  @NotNull
  public M markValue(@NotNull V value) {
    return getMarker(value);
  }

  /**
   * This method is called when 'Unmark Object' action is invoked.
   */
  public void unmarkValue(@NotNull V value, @NotNull M marker) {
  }

  public final Class<V> getValueClass() {
    return myValueClass;
  }
}
