package com.intellij.ide.util.treeView;

import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface MulticoloredItemPresentation extends ItemPresentation {

  /**
   * Returns a list of colored fragments representing the item text.
   *
   * @return the colored text fragments, or null if no color should be applied
   */
  @Nullable
  Collection<PresentableNodeDescriptor.ColoredFragment> getColoredFragments();
}
