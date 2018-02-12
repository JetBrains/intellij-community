/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.components;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Every component which would like to persist its state across IDEA restarts
 * should implement this interface.
 *
 * See <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">IntelliJ Platform SDK DevGuide</a>
 * for detailed description.
 *
 * In general, implementation should be thread-safe, because "loadState" is called from the same thread where component is initialized.
 * If component used only from one thread (e.g. EDT), thread-safe implementation is not required.
 */
public interface PersistentStateComponent<T> {
  /**
   * @return a component state. All properties, public and annotated fields are serialized. Only values, which differ
   * from default (i.e. the value of newly instantiated class) are serialized. {@code null} value indicates
   * that the returned state won't be stored, as a result previously stored state will be used.
   * @see com.intellij.util.xmlb.XmlSerializer
   */
  @Nullable
  T getState();

  /**
   * This method is called when new component state is loaded. The method can and will be called several times, if
   * config files were externally changed while IDEA running.
   * @param state loaded component state
   * @see com.intellij.util.xmlb.XmlSerializerUtil#copyBean(Object, Object) 
   */
  void loadState(@NotNull T state);

  /**
   * This method is called when component is initialized but no state is persisted.
   */
  default void noStateLoaded() {
  }
}
