// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Every component that would like to persist its state across IDE restarts should implement this interface.
 * <p>
 * See <a href="https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html">Persisting State of Components (IntelliJ Platform Docs)</a>
 * for detailed description.
 * <p>
 * <strong>DO NOT</strong> use for sensitive data
 * (see <a href="https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html">Persisting Sensitive Data</a>).
 * <p>
 * In general, an implementation should be thread-safe,
 * because "loadState" is called from the same thread where the component is initialized.
 * If a component is used only from one thread (e.g., EDT), thread-safe implementation is not required.
 *
 * @see SimplePersistentStateComponent
 */
public interface PersistentStateComponent<T> {
  /**
   * @return a component state. All properties, public and annotated fields are serialized.
   * Only values which differ from the default (i.e., the value of newly instantiated class) are serialized.
   * {@code null} value indicates that the returned state won't be stored, as a result previously stored state will be used.
   * @see com.intellij.util.xmlb.XmlSerializer
   */
  @Nullable T getState();

  /**
   * This method is called when a new component state is loaded.
   * The method can and will be called several times if config files are externally changed while the IDE is running.
   * <p>
   * State object should be used directly, defensive copying is not required.
   *
   * @param state loaded component state
   * @see com.intellij.util.xmlb.XmlSerializerUtil#copyBean(Object, Object)
   */
  void loadState(@NotNull T state);

  /**
   * This method is called when the component is initialized, but no state is persisted.
   */
  default void noStateLoaded() { }

  /**
   * If class also is a service, then this method will be called after loading state (even if no state)
   * but only once throughout the life cycle.
   */
  default void initializeComponent() { }
}
