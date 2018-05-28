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

import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;

import java.awt.*;


public class WelcomeFrameFixture extends ComponentFixture<WelcomeFrameFixture, FlatWelcomeFrame> {
  @NotNull
  public static WelcomeFrameFixture find(@NotNull Robot robot) {
    Pause.pause(new Condition("Welcome Frame to show up") {
      @Override
      public boolean test() {
        for (Frame frame : Frame.getFrames()) {
          if (frame instanceof FlatWelcomeFrame && frame.isShowing()) {
            return true;
          }
        }
        return false;
      }
    }, GuiTestUtil.INSTANCE.getLONG_TIMEOUT());

    for (Frame frame : Frame.getFrames()) {
      if (frame instanceof FlatWelcomeFrame && frame.isShowing()) {
        return new WelcomeFrameFixture(robot, (FlatWelcomeFrame)frame);
      }
    }
    throw new ComponentLookupException("Unable to find 'Welcome' window");
  }

  private WelcomeFrameFixture(@NotNull Robot robot, @NotNull FlatWelcomeFrame target) {
    super(WelcomeFrameFixture.class, robot, target);
  }

  @NotNull
  public WelcomeFrameFixture createNewProject() {
    findActionLinkByActionId("WelcomeScreen.CreateNewProject").click();
    return this;
  }

  @NotNull
  public WelcomeFrameFixture importProject() {
    findActionLinkByActionId("WelcomeScreen.ImportProject").click();
    return this;
  }

  @NotNull
  public WelcomeFrameFixture checkoutFrom(){
    findActionLinkByActionId("WelcomeScreen.GetFromVcs").click();
    return this;
  }

  @NotNull
  private ActionLinkFixture findActionLinkByActionId(String actionId) {
    return ActionLinkFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), target(), title);
  }
}
