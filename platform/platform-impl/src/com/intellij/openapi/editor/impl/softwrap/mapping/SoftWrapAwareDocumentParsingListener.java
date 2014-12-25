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

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for the entity that is interested in document processing. Such an interest may be imposed, for example,
 * by necessity to perform caching in order to increase performance.
 * <p/>
 * I.e. the main idea of effective document processing is to separate document parsing logic and its clients. This interface
 * defines contract for those clients.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 10:19:49 AM
 */
interface SoftWrapAwareDocumentParsingListener {

  /**
   * Notifies about editor position that points to the visual line start.
   * <p/>
   * Target visual line is assumed to belong to one of the categories below:
   * <pre>
   * <ul>
   *   <li>it contains fold region;</li>
   *   <li>it contains tabulation symbols;</li>
   *   <li>it ends with soft wrap;</li>
   * </ul>
   * </pre>
   *
   * @param position    position that points to the visual line start
   */
  void onVisualLineStart(@NotNull EditorPosition position);

  /**
   * Notifies about editor position that points to the visual line end
   * 
   * @param position      position that points to the visual line end
   */
  void onVisualLineEnd(@NotNull EditorPosition position);

  /**
   * Notifies about processed fold region.
   *
   * @param foldRegion                        processed fold region
   * @param collapsedFoldingWidthInColumns    width in columns of the symbols of the given fold regions
   * @param visualLine                        visual line where given fold region is located
   */
  void onCollapsedFoldRegion(@NotNull FoldRegion foldRegion, int collapsedFoldingWidthInColumns, int visualLine);

  /**
   * Notifies about tabulation symbol encountered during document parsing.
   * <p/>
   * Tabulations are treated specially because they may occupy different number of visual columns during representation
   * at IJ editor.
   * 
   * @param position        tabulation symbol position 
   * @param widthInColumns  width in visual columns of the tabulation symbol identified by the given position
   */
  void onTabulation(@NotNull EditorPosition position, int widthInColumns);
  
  /**
   * Notifies about soft wrap-introduced virtual line feed.
   *
   * @param position   position just before soft wrap
   */
  void beforeSoftWrapLineFeed(@NotNull EditorPosition position);
  
  /**
   * Notifies about soft wrap-introduced virtual line feed.
   *
   * @param position   position just after soft wrap
   */
  void afterSoftWrapLineFeed(@NotNull EditorPosition position);

  /**
   * There is a possible case that document parser steps back to particular offset, e.g. it may encounter a situation
   * when long line should be soft-wrapped and the most appropriate place for a wrap is located couple of symbols below
   * the current parsing position.
   * <p/>
   * Parsing listener is expected to drop all information for the document segment from given offset and offset used last time
   *
   * @param offset          offset that will be used as a starting point for document parsing
   * @param visualLine      visual line where given offset is located
   */
  void revertToOffset(int offset, int visualLine);

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
