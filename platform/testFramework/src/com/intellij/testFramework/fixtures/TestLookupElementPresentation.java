/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.ui.DeferredIcon;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
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
