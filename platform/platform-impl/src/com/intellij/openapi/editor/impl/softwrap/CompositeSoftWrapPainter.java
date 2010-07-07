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

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.ColorProvider;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType.AFTER_SOFT_WRAP;
import static com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED;
import static java.util.Arrays.asList;

/**
 * Encapsulates logic of wrapping multiple {@link SoftWrapPainter} implementations; chooses the one to use and deleagtes all
 * processing to it.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jul 2, 2010 10:20:14 AM
 */
public class CompositeSoftWrapPainter implements SoftWrapPainter {

  private static final List<Map<SoftWrapDrawingType, Character>> SYMBOLS = asList(
    asMap(asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
          asList('\uE48B',                   '\uE48C')),
    asMap(asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
          asList('\u2926',                   '\u2925')),
    asMap(asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
          asList('\u21B2',                   '\u21B3')),
    asMap(asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
          asList('\u2936',                   '\u2937')),
    asMap(asList(BEFORE_SOFT_WRAP_LINE_FEED, AFTER_SOFT_WRAP),
          asList('\u21A9',                   '\u21AA'))
  );

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

  @Override
  public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    initDelegateIfNecessary();
    if (!myEditor.getSettings().isSoftWrapsShown()) {
      int visualLine = y / lineHeight;
      LogicalPosition position = myEditor.visualToLogicalPosition(new VisualPosition(visualLine, 0));
      if (position.line != myEditor.getCaretModel().getLogicalPosition().line) {
        return myDelegate.getDrawingHorizontalOffset(g, drawingType, x, y, lineHeight);
      }
    }
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
      ColorProvider colorHolder = ColorProvider.byColorScheme(myEditor, EditorColors.RIGHT_MARGIN_COLOR, EditorColors.WHITESPACES_COLOR);
      myDelegate = new TextBasedSoftWrapPainter(SYMBOLS.get(mySymbolsDrawingIndex), myEditor, callback, colorHolder);
      initDelegateIfNecessary();
      return;
    }
    myDelegate = new ArrowSoftWrapPainter(myEditor);
  }

  private static <K, V> Map<K, V> asMap(Iterable<K> keys, Iterable<V> values) throws IllegalArgumentException {
    Map<K, V> result = new HashMap<K,V>();
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
