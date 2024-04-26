// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

/**
 * To check that component state is changed and must be saved, {@link #getState()} is called.
 * To reduce the {@code getState()} calls {@link com.intellij.openapi.util.ModificationTracker} can be implemented.
 * But a common interface can lead to confusion whether the meaning of "modification count"
 * for serialization and for other clients is different.
 * So, you can use this interface to distinguish use cases.
 */
public interface PersistentStateComponentWithModificationTracker<T> extends PersistentStateComponent<T> {
  long getStateModificationCount();
}
