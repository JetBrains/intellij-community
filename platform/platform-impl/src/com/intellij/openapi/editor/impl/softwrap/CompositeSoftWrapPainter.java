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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.ColorProvider;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType.AFTER_SOFT_WRAP;
import static com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED;
import static java.util.Arrays.asList;

/**
 * Encapsulates logic of wrapping multiple {@link SoftWrapPainter} implementations; chooses the one to use and delegates all
 * processing to it.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jul 2, 2010 10:20:14 AM
 */
public class CompositeSoftWrapPainter implements SoftWrapPainter {

  /**
   * Defines a key to use for checking for code of the custom unicode symbol to use for <code>'before soft wrap'</code> representation.
   * <p/>
   * Target value (if any) is assumed to be in hex format.
   */
  public static final String CUSTOM_BEFORE_SOFT_WRAP_SIGN_KEY = "idea.editor.wrap.soft.before.code";

  /**
   * Defines a key to use for checking for code of the custom unicode symbol to use for <code>'after soft wrap'</code> representation.
   * <p/>
   * Target value (if any) is assumed to be in hex format.
   */
  public static final String CUSTOM_AFTER_SOFT_WRAP_SIGN_KEY = "idea.editor.wrap.soft.after.code";

  private static final Logger LOG = Logger.getInstance("#" + CompositeSoftWrapPainter.class.getName());

  private static final List<Map<SoftWrapDrawingType, Character>> SYMBOLS = new ArrayList<>();

  static {
    // Pickup custom soft wraps drawing symbols if both of the are defined.
    Character customBeforeSymbol = parse(CUSTOM_BEFORE_SOFT_WRAP_SIGN_KEY);
    if (customBeforeSymbol != null) {
      Character customAfterSymbol = parse(CUSTOM_AFTER_SOFT_WRAP_SIGN_KEY);
      if (customAfterSymbol != null) {
        LOG.info(String.format("Picked up custom soft wrap drawing symbols: '%c' and '%c'", customBeforeSymbol, customAfterSymbol));
        SYMBOLS.add(asMap(
          asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
          asList(customBeforeSymbol,         customAfterSymbol))
        );
      }
    }

    SYMBOLS.add(asMap(
      asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
      asList('\u2926', '\u2925'))
    );
    SYMBOLS.add(asMap(
      asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
      asList('\u21B2',                   '\u21B3'))
    );
    SYMBOLS.add(asMap(
      asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
      asList('\u2936',                   '\u2937'))
    );
    SYMBOLS.add(asMap(
      asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
      asList('\u21A9',                   '\u21AA'))
    );
    SYMBOLS.add(asMap(
      asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
      asList('\uE48B',                   '\uE48C'))
    );
  }

  private final EditorEx        myEditor;
  private       SoftWrapPainter myDelegate;

  /**
   * There is a possible case that particular symbols configured to be used as a soft wrap drawings are not supported by
   * available fonts. We would like to try another symbols then.
   * <p/>
   * Current field points to the index of symbols collection from {@link #SYMBOLS} tried last time.
   */
  private int mySymbolsDrawingIndex = -1;

  public CompositeSoftWrapPainter(EditorEx editor) {
    myEditor = editor;
  }

  @Nullable
  private static Character parse(String key) {
    String value = System.getProperty(key);
    if (value == null) {
      return null;
    }

    value = value.trim();
    if (value.isEmpty()) {
      return null;
    }

    int code;
    try {
      code = Integer.parseInt(value, 16);
    }
    catch (NumberFormatException e) {
      LOG.info(String.format("Detected invalid code for system property '%s' - '%s'. Expected to find hex number there. " +
                               "Custom soft wraps signs will not be applied", key, value));
      return null;
    }

    return (char)code;
  }

  @Override
  public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    initDelegateIfNecessary();
    return myDelegate.paint(g, drawingType, x, y, lineHeight);
  }

  @Override
  public int getDrawingHorizontalOffset(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    initDelegateIfNecessary();
    return myDelegate.getDrawingHorizontalOffset(g, drawingType, x, y, lineHeight);
  }

  @Override
  public int getMinDrawingWidth(@NotNull SoftWrapDrawingType drawingType) {
    initDelegateIfNecessary();
    return myDelegate.getMinDrawingWidth(drawingType);
  }

  @Override
  public boolean canUse() {
    return true;
  }

  private void initDelegateIfNecessary() {
    if (myDelegate != null && myDelegate.canUse()) {
      return;
    }
    if (++mySymbolsDrawingIndex < SYMBOLS.size()) {
      TextDrawingCallback callback = myEditor.getTextDrawingCallback();
      ColorProvider colorHolder = ColorProvider.byColorScheme(myEditor, EditorColors.SOFT_WRAP_SIGN_COLOR);
      myDelegate = new TextBasedSoftWrapPainter(SYMBOLS.get(mySymbolsDrawingIndex), myEditor, callback, colorHolder);
      initDelegateIfNecessary();
      return;
    }
    myDelegate = new ArrowSoftWrapPainter(myEditor);
  }
  
  public void reinit() {
    myDelegate = null;
    mySymbolsDrawingIndex = -1;
  }

  private static <K, V> Map<K, V> asMap(Iterable<K> keys, Iterable<V> values) throws IllegalArgumentException {
    Map<K, V> result = new HashMap<>();
    Iterator<K> keyIterator = keys.iterator();
    Iterator<V> valueIterator = values.iterator();
    while (keyIterator.hasNext()) {
      if (!valueIterator.hasNext()) {
        throw new IllegalArgumentException(
          String.format("Can't build for the given data. Reason: number of keys differs from number of values. "
                        + "Keys: %s, values: %s", keys, values)
        );
      }
      result.put(keyIterator.next(), valueIterator.next());
    }

    if (valueIterator.hasNext()) {
      throw new IllegalArgumentException(
        String.format("Can't build for the given data. Reason: number of keys differs from number of values. "
                      + "Keys: %s, values: %s", keys, values)
      );
    }
    return result;
  }
}
