// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.ui.DeferredIcon;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.icons.CompositeIcon;
import com.intellij.ui.icons.RowIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class TestLookupElementPresentation extends LookupElementPresentation {
  public static @NotNull TestLookupElementPresentation renderReal(@NotNull LookupElement e) {
    TestLookupElementPresentation p = new TestLookupElementPresentation();
    //noinspection rawtypes
    LookupElementRenderer renderer = e.getExpensiveRenderer();
    if (renderer != null) {
      //noinspection unchecked
      renderer.renderElement(e, p);
    } else {
      e.renderElement(p);
    }
    return p;
  }

  public static @Nullable Icon unwrapIcon(@Nullable Icon icon) {
    while (true) {
      if  (icon instanceof RowIcon) {
        if (((CompositeIcon)icon).getIconCount() == 0) {
          return icon;
        }
        else {
          icon = ((RowIcon)icon).getIcon(0);
        }
      }
      else if (icon instanceof DeferredIcon) {
        icon = ((DeferredIcon)icon).evaluate();
      }
      else if (icon instanceof LayeredIcon) {
        icon = ((LayeredIcon)icon).getIcon(0);
      }
      else {
        return icon;
      }
    }
  }
}
