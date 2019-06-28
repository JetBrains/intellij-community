// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.ui.DeferredIcon;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.icons.RowIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class TestLookupElementPresentation extends LookupElementPresentation {

  @NotNull
  public static TestLookupElementPresentation renderReal(@NotNull LookupElement e) {
    TestLookupElementPresentation p = new TestLookupElementPresentation() {
      @Override
      public boolean isReal() {
        return true;
      }
    };
    e.renderElement(p);
    return p;
  }

  @Nullable
  public static Icon unwrapIcon(@Nullable Icon icon) {
    while (true) {
      if (icon instanceof RowIcon) icon = ((RowIcon)icon).getIcon(0);
      else if (icon instanceof DeferredIcon) icon = ((DeferredIcon)icon).evaluate();
      else if (icon instanceof LayeredIcon) icon = ((LayeredIcon)icon).getIcon(0);
      else return icon;
    }
  }

}
