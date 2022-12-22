// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.MethodHandleUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import sun.font.FontDesignMetrics;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

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
    private final MethodHandle myHandleCharWidthMethod;
    private final MethodHandle myGetLatinCharWidthMethod;

    private DefaultFontLayoutService() {
      MethodHandle handleCharWidthMethod;
      try {
        handleCharWidthMethod =
          MethodHandleUtil.getPrivateMethod(FontDesignMetrics.class, "handleCharWidth", MethodType.methodType(Float.TYPE, Integer.TYPE));
      }
      catch (Throwable e) {
        handleCharWidthMethod = null;
        LOG.warn("Couldn't access FontDesignMetrics.handleCharWidth method", e);
      }
      myHandleCharWidthMethod = handleCharWidthMethod;

      MethodHandle getLatinCharWidthMethod;
      try {
        getLatinCharWidthMethod =
          MethodHandleUtil.getPrivateMethod(FontDesignMetrics.class, "getLatinCharWidth", MethodType.methodType(Float.TYPE, Character.TYPE));
      }
      catch (Throwable e) {
        getLatinCharWidthMethod = null;
        LOG.warn("Couldn't access FontDesignMetrics.getLatinCharWidth method", e);
      }

      myGetLatinCharWidthMethod = getLatinCharWidthMethod;
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
            return (float)myGetLatinCharWidthMethod.invokeExact((FontDesignMetrics)fontMetrics, (char)codePoint);
          }
          catch (Throwable e) {
            LOG.debug(e);
          }
        }
        if (myHandleCharWidthMethod != null) {
          try {
            return (float)myHandleCharWidthMethod.invokeExact((FontDesignMetrics)fontMetrics, codePoint);
          }
          catch (Throwable e) {
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
