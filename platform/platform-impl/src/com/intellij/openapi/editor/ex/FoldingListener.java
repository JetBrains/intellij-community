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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.NotNull;

/**
 * Defines common contract for clients interested in folding processing.
 *
 * @author Denis Zhdanov
 * @since Sep 8, 2010 11:20:28 AM
 */
public interface FoldingListener {

  /**
   * Informs that <code>'collapsed'</code> state of given fold region is just changed.
   *
   * @param region    fold region that is just collapsed or expanded
   */
  void onFoldRegionStateChange(@NotNull FoldRegion region);
}
