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
package git4idea.history.browser;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class FictiveTableCellRenderer extends ColoredListCellRenderer {
  // for cases when our renderer is encapsulated
  protected abstract int getParentWidth(JList list);
  @Nullable
  protected abstract Color getBgrndColor(JList list, Object value, int index, boolean selected, boolean hasFocus);
  protected abstract Description getDescription(Object value);
  protected abstract Trinity<String, SimpleTextAttributes, Object> getMoreTag();
  protected abstract boolean willRender(Object value);

  protected abstract static class Description {
    private final java.util.List<Pair<String, SimpleTextAttributes>> myPieces;
    private final int myMiddleIdx;

    public Description(int middleIdx, java.util.List<Pair<String, SimpleTextAttributes>> pieces) {
      myMiddleIdx = middleIdx;
      myPieces = pieces;
    }

    public int getMiddleIdx() {
      return myMiddleIdx;
    }

    public java.util.List<Pair<String, SimpleTextAttributes>> getPieces() {
      return myPieces;
    }

    public abstract String getMaxString(final int idx);
  }

  @Override
  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    if (! willRender(value)) return;
    final int parentWidth = getParentWidth(list);
    final Color bgrndColor = getBgrndColor(list, value, index, selected, hasFocus);
    if (bgrndColor != null) {
      setBackground(bgrndColor);
    }

    final Description description = getDescription(value);

    final MyFontHelper helper = new MyFontHelper(list);

    int fixedWidth = 0;
    int fixedWidthBefore = 0;
    int changeableWidth = 0;
    final java.util.List<Pair<String,SimpleTextAttributes>> pieces = description.getPieces();
    final int middleIdx = description.getMiddleIdx();
    for (int i = 0; i < pieces.size(); i++) {
      final Pair<String, SimpleTextAttributes> pair = pieces.get(i);
      if (middleIdx == i) {
        changeableWidth = helper.getWidth(pair);
      } else {
        final int width = helper.getWidth(pair);

        if (i < middleIdx) {
          fixedWidthBefore += width;
          append(pair.getFirst(), pair.getSecond());
          final String adj = description.getMaxString(i);
          if (adj != null) {
            final int adjWidth = helper.getWidth(new Pair<String, SimpleTextAttributes>(adj, pair.getSecond()));
            appendAlign(adjWidth);
            fixedWidth += adjWidth;
          } else {
            appendAlign(width);
            fixedWidth += width;
          }
        } else {
          fixedWidth += width;
        }
      }
    }

    final Pair<String, SimpleTextAttributes> changeable = pieces.get(middleIdx);
    final int difference = parentWidth - fixedWidth;
    if ((fixedWidth + changeableWidth) > parentWidth) {
      // need zipping
      if (fixedWidth < parentWidth) {
        // todo this should be rewised
        final Trinity<String, SimpleTextAttributes, Object> tag = getMoreTag();
        final int more = helper.getWidth(new Pair<String, SimpleTextAttributes>(tag.getFirst(), tag.getSecond()));
        if (more < difference) {
          final String truncated = CommittedChangeListRenderer
            .truncateDescription(changeable.getFirst(), helper.getFontMetrics(changeable.getSecond()), difference - more);
          
          final int truncatedWidth = helper.getWidth(new Pair<String, SimpleTextAttributes>(truncated, changeable.getSecond()));
          append(truncated, changeable.getSecond());
          append(tag.getFirst(), tag.getSecond(), tag.getThird());
          if (truncatedWidth > 0) {
            appendAlign(difference - truncatedWidth);
          }
        } else {
          appendAlign(difference);
        }
      }
    } else {
      append(changeable.getFirst(), changeable.getSecond());
      appendAlign(parentWidth - fixedWidth);
    }

    for (int i = middleIdx + 1; i < pieces.size(); i++) {
      final Pair<String, SimpleTextAttributes> pair = pieces.get(i);
      append(pair.getFirst(), pair.getSecond());
    }
  }

  private static class MyFontHelper {
    private final FontMetrics myFontMetrics;
    private final FontMetrics myBoldMetrics;
    private final FontMetrics myItalicsMetrics;
    private final FontMetrics myBoldItalicsMetrics;

    private MyFontHelper(final JList list) {
      final Font font = list.getFont();
      myFontMetrics = list.getFontMetrics(font);
      myBoldMetrics = list.getFontMetrics(font.deriveFont(Font.BOLD));
      myItalicsMetrics = list.getFontMetrics(font.deriveFont(Font.ITALIC));
      myBoldItalicsMetrics = list.getFontMetrics(font.deriveFont(Font.ITALIC | Font.BOLD));
    }

    public int getWidth(final Pair<String, SimpleTextAttributes> pair) {
      final SimpleTextAttributes ta = pair.getSecond();
      FontMetrics fm;
      fm = getFontMetrics(ta);
      return fm.stringWidth(pair.getFirst());
    }

    public FontMetrics getFontMetrics(SimpleTextAttributes ta) {
      FontMetrics fm;
      if ((ta.getStyle() & SimpleTextAttributes.STYLE_BOLD) == SimpleTextAttributes.STYLE_BOLD) {
        if ((ta.getStyle() & SimpleTextAttributes.STYLE_ITALIC) == SimpleTextAttributes.STYLE_ITALIC) {
          fm = myBoldItalicsMetrics;
        } else {
          fm = myBoldMetrics;
        }
      } else {
        if ((ta.getStyle() & SimpleTextAttributes.STYLE_ITALIC) == SimpleTextAttributes.STYLE_ITALIC) {
          fm = myItalicsMetrics;
        } else {
          fm = myFontMetrics;
        }
      }
      return fm;
    }
  }
}
