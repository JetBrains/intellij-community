/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.font.FontRenderContext;

class TextFragmentFactory {
  public static TextFragment createTextFragment(@NotNull char[] lineChars, int start, int end, boolean isRtl,
                                                @NotNull FontInfo fontInfo, @NotNull FontRenderContext fontRenderContext) {
    if (isRtl || fontInfo.getFont().hasLayoutAttributes() || isComplexText(lineChars, start, end)) {
      return new ComplexTextFragment(lineChars, start, end, isRtl, fontInfo.getFont(), fontRenderContext);
    }
    else {
      return new SimpleTextFragment(lineChars, start, end, fontInfo);
    }
  }

  private static boolean isComplexText(char[] chars, int start, int end) {
    for (int i = start; i < end; i++) {
      // Unicode characters with code points below 0x0300 don't combine with each other on rendering, so they can be processed one-by-one  
      if (chars[i] >= 0x0300) return true;
    }
    return false;
    
  }
}
