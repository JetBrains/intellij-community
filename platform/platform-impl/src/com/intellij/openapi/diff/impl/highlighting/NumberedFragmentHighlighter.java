package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.diff.impl.FragmentNumberGutterIconRenderer;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.FragmentHighlighterImpl;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;

/**
 * @author irengrig
 *         Date: 8/12/11
 *         Time: 4:01 PM
 */
public class NumberedFragmentHighlighter extends FragmentHighlighterImpl {
  private final boolean myDrawNumber;
  private int myCounter;

  public NumberedFragmentHighlighter(DiffMarkup appender1, DiffMarkup appender2, boolean drawNumber) {
    super(appender1, appender2);
    myDrawNumber = drawNumber;
    myCounter = 0;
  }

  private GutterIconRenderer createRenderer(Fragment fragment) {
    final TextAttributesKey key = getColorAttributesKey(fragment.getType());
    if (key != null) {
      final FragmentNumberGutterIconRenderer renderer =
        new FragmentNumberGutterIconRenderer(myCounter + 1, key, myAppender1.getEditor().getScrollPane());
      ++ myCounter;
      return renderer;
    }
    return null;
  }

  private TextAttributesKey getColorAttributesKey(final TextDiffTypeEnum textDiffTypeEnum) {
    if (TextDiffTypeEnum.CHANGED.equals(textDiffTypeEnum)) {
      return DiffColors.DIFF_MODIFIED;
    } else if (TextDiffTypeEnum.INSERT.equals(textDiffTypeEnum)) {
      return DiffColors.DIFF_INSERTED;
    } else if (TextDiffTypeEnum.DELETED.equals(textDiffTypeEnum)) {
      return DiffColors.DIFF_DELETED;
    } else if (TextDiffTypeEnum.CONFLICT.equals(textDiffTypeEnum)) {
      return DiffColors.DIFF_CONFLICT;
    } else {
      return null;
    }
  }

  @Override
  protected void highlightFragmentImpl(Fragment fragment, boolean drawBorder) {
    final GutterIconRenderer renderer = myDrawNumber ? createRenderer(fragment) : null;

    myAppender1.highlightText(fragment, drawBorder, renderer);
    myAppender2.highlightText(fragment, drawBorder, renderer);
  }

  public void reset() {
    myCounter = 0;
  }
}
