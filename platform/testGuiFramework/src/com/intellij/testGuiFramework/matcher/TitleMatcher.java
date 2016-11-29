/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.matcher;

import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;

/**
 * @author Sergey Karashevich
 */
public class TitleMatcher<T extends JDialog> extends GenericTypeMatcher<T> {

  String myTitle;

  public TitleMatcher(@Nonnull Class<T> supportedType) {
    super(supportedType);
  }

  public TitleMatcher(@Nonnull Class<T> supportedType, @NotNull String title) {
    super(supportedType);
    myTitle = title;
  }

  @Override
  protected boolean isMatching(@Nonnull T t) {
    return (t.getTitle().equals(myTitle) && t.isShowing());
  }

  public static TitleMatcher<JDialog> withTitleMatcher(@NotNull String title){
    return new TitleMatcher<>(JDialog.class, title);
  }
}
