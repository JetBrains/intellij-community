// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import sun.font.FontDesignMetrics;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.lang.reflect.Method;

/**
 * Encapsulates logic related to font metrics. Mock instance can be used in tests to make them independent on font properties on particular
 * platform.
 */
public abstract class FontLayoutService {
  private static final Logger LOG = Logger.getInstance(FontLayoutService.class);

  private static final FontLayoutService DEFAULT_INSTANCE = new DefaultFontLayoutService();
  private static FontLayoutService INSTANCE = DEFAULT_INSTANCE;

  public static FontLayoutService getInstance() {
    return INSTANCE;
  }

  @NotNull
  public abstract GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext,
                                                char @NotNull [] chars, int start, int end, boolean isRtl);

  public abstract int charWidth(@NotNull FontMetrics fontMetrics, char c);

  public abstract int charWidth(@NotNull FontMetrics fontMetrics, int codePoint);

  public abstract float charWidth2D(@NotNull FontMetrics fontMetrics, int codePoint);

  public abstract int stringWidth(@NotNull FontMetrics fontMetrics, @NotNull String str);

  public abstract int getHeight(@NotNull FontMetrics fontMetrics);

  public abstract int getDescent(@NotNull FontMetrics fontMetrics);

  @TestOnly
  public static void setInstance(@Nullable FontLayoutService fontLayoutService) {
    INSTANCE = fontLayoutService == null ? DEFAULT_INSTANCE : fontLayoutService;
  }

  private static final class DefaultFontLayoutService extends FontLayoutService {
    private final Method myHandleCharWidthMethod;
    private final Method myGetLatinCharWidthMethod;

    private DefaultFontLayoutService() {
      myHandleCharWidthMethod = ReflectionUtil.getDeclaredMethod(FontDesignMetrics.class, "handleCharWidth", int.class);
      if (myHandleCharWidthMethod == null) {
        LOG.warn("Couldn't access FontDesignMetrics.handleCharWidth method");
      }
      myGetLatinCharWidthMethod = ReflectionUtil.getDeclaredMethod(FontDesignMetrics.class, "getLatinCharWidth", char.class);
      if (myGetLatinCharWidthMethod == null) {
        LOG.warn("Couldn't access FontDesignMetrics.getLatinCharWidth method");
      }
    }

    @NotNull
    @Override
    public GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext,
                                         char @NotNull [] chars, int start, int end, boolean isRtl) {
      return font.layoutGlyphVector(fontRenderContext, chars, start, end, (isRtl ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT));
    }

    @Override
    public int charWidth(@NotNull FontMetrics fontMetrics, char c) {
      return fontMetrics.charWidth(c);
    }

    @Override
    public int charWidth(@NotNull FontMetrics fontMetrics, int codePoint) {
      return fontMetrics.charWidth(codePoint);
    }

    @Override
    public float charWidth2D(@NotNull FontMetrics fontMetrics, int codePoint) {
      if (fontMetrics instanceof FontDesignMetrics) {
        if (codePoint < 256 && myGetLatinCharWidthMethod != null) {
          try {
            return (float)myGetLatinCharWidthMethod.invoke(fontMetrics, (char)codePoint);
          }
          catch (Exception e) {
            LOG.debug(e);
          }
        }
        if (myHandleCharWidthMethod != null) {
          try {
            return (float)myHandleCharWidthMethod.invoke(fontMetrics, codePoint);
          }
          catch (Exception e) {
            LOG.debug(e);
          }
        }
      }
      return charWidth(fontMetrics, codePoint);
    }

    @Override
    public int stringWidth(@NotNull FontMetrics fontMetrics, @NotNull String str) {
      return fontMetrics.stringWidth(str);
    }

    @Override
    public int getHeight(@NotNull FontMetrics fontMetrics) {
      return fontMetrics.getHeight();
    }

    @Override
    public int getDescent(@NotNull FontMetrics fontMetrics) {
      return fontMetrics.getDescent();
    }
  }
}
