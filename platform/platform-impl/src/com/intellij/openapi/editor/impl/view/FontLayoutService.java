/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;

/**
 * Encapsulates logic related to font metrics. Mock instance can be used in tests to make them independent on font properties on particular
 * platform.
 */
public abstract class FontLayoutService {
  private static final FontLayoutService DEFAULT_INSTANCE = new DefaultFontLayoutService();
  private static FontLayoutService INSTANCE = DEFAULT_INSTANCE;
  
  public static FontLayoutService getInstance() {
    return INSTANCE;
  }
  
  @NotNull
  public abstract GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext, 
                                                @NotNull char[] chars, int start, int end, boolean isRtl); 
  
  public abstract int charWidth(@NotNull FontMetrics fontMetrics, char c);
  
  public abstract int getHeight(@NotNull FontMetrics fontMetrics);
  
  public abstract int getAscent(@NotNull FontMetrics fontMetrics);
  
  @TestOnly
  public static void setInstance(@Nullable FontLayoutService fontLayoutService) {
    INSTANCE = fontLayoutService == null ? DEFAULT_INSTANCE : fontLayoutService;
  }
  
  private static class DefaultFontLayoutService extends FontLayoutService {
    @NotNull
    @Override
    public GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext,
                                         @NotNull char[] chars, int start, int end, boolean isRtl) {
      return font.layoutGlyphVector(fontRenderContext, chars, start, end, isRtl ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT);
    }

    @Override
    public int charWidth(@NotNull FontMetrics fontMetrics, char c) {
      return fontMetrics.charWidth(c);
    }

    @Override
    public int getHeight(@NotNull FontMetrics fontMetrics) {
      return fontMetrics.getHeight();
    }

    @Override
    public int getAscent(@NotNull FontMetrics fontMetrics) {
      return fontMetrics.getAscent();
    }
  }
}
