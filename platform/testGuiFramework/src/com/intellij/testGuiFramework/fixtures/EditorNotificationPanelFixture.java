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

import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import org.fest.reflect.core.Reflection;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

public class EditorNotificationPanelFixture extends JComponentFixture<EditorNotificationPanelFixture, EditorNotificationPanel> {
  public EditorNotificationPanelFixture(@NotNull Robot robot, @NotNull EditorNotificationPanel target) {
    super(EditorNotificationPanelFixture.class, robot, target);
  }

  public void performAction(@NotNull final String label) {
    HyperlinkLabel link = robot().finder().find(target(), new GenericTypeMatcher<HyperlinkLabel>(HyperlinkLabel.class) {
      @Override
      protected boolean isMatching(@NotNull HyperlinkLabel hyperlinkLabel) {
        // IntelliJ's HyperLinkLabel class does not expose the getText method (it is package private)
        return hyperlinkLabel.isShowing() &&
               label.equals(Reflection.method("getText").withReturnType(String.class).in(hyperlinkLabel).invoke());
      }
    });
    driver().click(link);
  }
}
