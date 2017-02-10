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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.testGuiFramework.driver.SearchTextFieldDriver;
import com.intellij.ui.SearchTextField;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.AbstractJComponentFixture;
import org.jetbrains.annotations.NotNull;

public class SearchTextFieldFixture extends AbstractJComponentFixture<SearchTextFieldFixture, SearchTextField, SearchTextFieldDriver> {
  public SearchTextFieldFixture(@NotNull Robot robot,
                                @NotNull SearchTextField target) {
    super(SearchTextFieldFixture.class, robot, target);
  }

  @NotNull
  public SearchTextFieldFixture enterText(@NotNull String text) {
    driver().enterText(target(), text);

    return this;
  }

  @NotNull
  public SearchTextFieldFixture requireText(@NotNull String text) {
    driver().requireText(target(), text);

    return this;
  }

  @NotNull
  @Override
  protected SearchTextFieldDriver createDriver(Robot robot) {
    return new SearchTextFieldDriver(robot);
  }
}