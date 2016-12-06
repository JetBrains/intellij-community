/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import org.jetbrains.annotations.NotNull;

interface SoftWrapAwareDocumentParsingListener {

  /**
   * Notifies current listener that particular document region re-parsing is about to begin.
   * 
   * @param event   object that contains information about re-parsed document region
   */
  void onCacheUpdateStart(@NotNull IncrementalCacheUpdateEvent event);

  /**
   * Notifies current listener that particular document region re-parsing has just finished.
   *
   * @param event   object that contains information about re-parsed document region
   */
  void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event);

  /**
   * Notifies current listener that all dirty regions for the current editor have been recalculated.
   * <p/>
   * It differs from {@link #onRecalculationEnd(IncrementalCacheUpdateEvent)} because there is a possible case that there
   * is more than one 'dirty' region which is necessary to recalculate.
   * {@link #onRecalculationEnd(IncrementalCacheUpdateEvent)} will be called after every region recalculation then
   * and current method will be called one time when all recalculations have been performed.
   */
  void recalculationEnds();
  
  /**
   * Callback for asking to drop all cached information (if any).
   */
  void reset();
}
