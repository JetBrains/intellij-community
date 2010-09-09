/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
   * Notifies about processed symbol. Please note that given context points to the end of line (not to the start of the new line) if
   * {@link ProcessingContext#symbol processed symbol} is line feed.
   *
   * @param context    context that points to the {@link ProcessingContext#symbol processed symbol}
   */
  void onProcessedSymbol(@NotNull ProcessingContext context);

  /**
   * Notifies about processed fold region.
   *
   * @param foldRegion                        processed fold region
   * @param collapsedFoldingWidthInColumns    width in columns of the symbols of the given fold regions
   * @param visualLine                        visual line where given fold region is located
   */
  void onCollapsedFoldRegion(@NotNull FoldRegion foldRegion, int collapsedFoldingWidthInColumns, int visualLine);

  /**
   * Notifies that soft wrap is encountered.
   *
   * @param context   context that points to document position just before soft wrap
   */
  void beforeSoftWrap(@NotNull ProcessingContext context);

  /**
   * Notifies about soft wrap-introduced line feed.
   *
   * @param context   context that points to document position just after soft wrap-introduced line feed
   */
  void afterSoftWrapLineFeed(@NotNull ProcessingContext context);

  /**
   * There is a possible case that document parser steps back to particular offset, i.e. it may encounter a situation
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
   * Notifies that given document range is about to be recalculated.
   * <p/>
   * I.e. the listener should expect the following calls sequence during range recalculation:
   * <p/>
   * <pre>
   * <ul>
   *   <li>{@link #onRecalculationStart(int, int)};</li>
   *   <li>number of calls like {@link #onProcessedSymbol(ProcessingContext)}, {@link #onCollapsedFoldRegion(FoldRegion, int, int)} etc;</li>
   *   <li>{@link #onRecalculationEnd(int, int)};</li>
   * </ul>
   * </pre>
   *
   * @param startOffset   start offset of document range that is about to be recalculated
   * @param endOffset     end offset of document range that is about to be recalculated
   */
  void onRecalculationStart(int startOffset, int endOffset);

  /**
   * Notifies that given document range is recalculated.
   * <p/>
   * <b>Note:</b> given offsets may differ from the one given to {@link #onRecalculationStart(int, int)}. E.g. there is a possible
   * case that user removes particular block of text. {@link #onRecalculationStart(int, int)} is called with offsets of logical lines
   * that hold that block and this method is called with //TODO den add doc
   *
   * @param startOffset   start offset of document range that is recalculated
   * @param endOffset     end offset of document range that is recalculated
   */
  void onRecalculationEnd(int startOffset, int endOffset);
}
