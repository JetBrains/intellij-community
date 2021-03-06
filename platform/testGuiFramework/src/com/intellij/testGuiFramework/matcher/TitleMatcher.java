// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.matcher;

import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Sergey Karashevich
 */
public class TitleMatcher<T extends JDialog> extends GenericTypeMatcher<T> {

  String myTitle;

  public TitleMatcher(@NotNull Class<T> supportedType) {
    super(supportedType);
  }

  public TitleMatcher(@NotNull Class<T> supportedType, @NotNull String title) {
    super(supportedType);
    myTitle = title;
  }

  @Override
  protected boolean isMatching(@NotNull T t) {
    return (t.getTitle().equals(myTitle) && t.isShowing());
  }

  public static TitleMatcher<JDialog> withTitleMatcher(@NotNull String title){
    return new TitleMatcher<>(JDialog.class, title);
  }
}
