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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingListener;

/**
 * Adapts {@link FoldingListener} to soft wraps specific.
 * <p/>
 * Generally, replaces {@link FoldingListener#onFoldRegionStateChange(FoldRegion)} by {@link #onFoldRegionStateChange(int, int)}.
 * The reason is that soft wraps are assumed to be processed after fold regions, i.e. every time the document is changed, folding
 * is processed at first place (notifying soft wraps via {@link FoldingListener}) and soft wraps are assumed to be processed
 * only on {@link #onFoldProcessingEnd()}. Hence, there is a possible case that changed {@link FoldRegion} object
 * is out-of-date (e.g. its offsets info is dropped if the region is removed), so, we can't use
 * {@link FoldingListener#onFoldRegionStateChange(FoldRegion)}.
 * 
 * @author Denis Zhdanov
 * @since 5/4/11 3:48 PM
 */
public interface SoftWrapFoldingListener {

  /**
   * Informs that <code>'collapsed'</code> state of fold region that is/was located at the target range is just changed.
   * <p/>
   * <b>Note:</b> listener should delay fold region state processing until {@link #onFoldProcessingEnd()} is called.
   * I.e. folding model may return inconsistent data between current moment and {@link #onFoldProcessingEnd()}.
   *
   * @param startOffset     start offset of the target fold region (inclusive)
   * @param endOffset       end offset of the target fold region (exclusive)
   */
  void onFoldRegionStateChange(int startOffset, int endOffset);

  /**
   * Informs that fold processing is done.
   */
  void onFoldProcessingEnd();
}
