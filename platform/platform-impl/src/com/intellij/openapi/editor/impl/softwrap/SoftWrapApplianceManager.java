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
package com.intellij.openapi.editor.impl.softwrap;

import org.jetbrains.annotations.NotNull;

/**
 * Defines a contract for the service that receives notifications about requests to represent particular text and
 * creates and registers new soft wraps for that if necessary.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jul 5, 2010 9:46:12 AM
 */
public interface SoftWrapApplianceManager {

  /**
   * Defines a callback that is invoked on request to draw target text fragment and that can register new soft wraps in order
   * to correctly represent it.
   * <p/>
   * Target text fragment to represent belongs to the given char array and lays at <code>[start; end)</code> interval.
   * <p/>
   * Please note that it's possible for soft wrap to occur inside <code>[start; end)</code> region - e.g. there is a possible
   * case that particular single token is too long and we want to split it.
   * <p/>
   * <b>Note:</b> it's assumed that this method is called only on editor repainting.
   *
   * @param chars     target text holder
   * @param start     start offset of the token to process within the given char array (inclusive)
   * @param end       end offset of the token to process within the given char array (exclusive)
   * @param x         <code>'x'</code> coordinate within the given graphics buffer that will be used to start drawing the text
   * @param fontType  font type used for the target text fragment representation
   */
  void registerSoftWrapIfNecessary(@NotNull char[] chars, int start, int end, int x, int fontType);
}
