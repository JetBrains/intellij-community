/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.components;

/**
 * To check that component stat is changed and must be saved, `getState` is called.
 * To reduce `getState` calls {@link com.intellij.openapi.util.ModificationTracker} can be implemented.
 * But common interface can lead to confusion (if meaning of 'modification count' for serialization and for other clients is different).
 * So, you can use this interface to distinguish use cases.
 */
public interface PersistentStateComponentWithModificationTracker<T> extends PersistentStateComponent<T> {
  long getStateModificationCount();
}
