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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.ScrollingModel;

/**
 * Extends {@link ScrollingModel} with more implementation-specific functionality.
 * 
 * @author Denis Zhdanov
 * @since 1/18/11 10:30 AM
 */
public interface ScrollingModelEx extends ScrollingModel {

  /**
   * Asks current model to avoid changing viewport position and just remember it instead. That remembered position
   * may be applied later during {@link #flushViewportChanges()} processing.
   */
  void accumulateViewportChanges();

  /**
   * Does nothing if {@link #accumulateViewportChanges()} is called before it or if no requests for viewport location
   * change arrived since the last time {@link #accumulateViewportChanges()} is called.
   * <p/>
   * Applies remembered viewport location change request to the editor.
   */
  void flushViewportChanges();
}
