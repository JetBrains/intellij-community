package org.jetbrains.android.dom.animator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAnimatorUtil {
  private static final String[] TAG_NAMES = {"set", "objectAnimator", "animator"};

  public static List<String> getPossibleChildren() {
    return Arrays.asList(TAG_NAMES);
  }

  @Nullable
  public static String getStyleableNameByTagName(@NotNull String tagName) {
    if (tagName.equals("set")) {
      return "AnimatorSet";
    }
    else if (tagName.equals("objectAnimator")) {
      return "PropertyAnimator";
    }
    return null;
  }
}
