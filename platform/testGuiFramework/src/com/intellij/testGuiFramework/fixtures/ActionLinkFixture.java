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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.components.labels.ActionLink;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.MouseClickInfo;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComponentDriver;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Timeout;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.fest.swing.timing.Pause.pause;

public class ActionLinkFixture extends JComponentFixture<ActionLinkFixture, ActionLink> {


  @NotNull
  public static ActionLinkFixture findByActionId(@NotNull final String actionId,
                                                 @NotNull final Robot robot,
                                                 @NotNull final Container container) {
    final Ref<ActionLink> actionLinkRef = new Ref<ActionLink>();
    pause(new Condition("Find ActionLink with ID '" + actionId + "'") {
      @Override
      public boolean test() {
        Collection<ActionLink> found = robot.finder().findAll(container, new GenericTypeMatcher<ActionLink>(ActionLink.class) {
          @Override
          protected boolean isMatching(@NotNull ActionLink actionLink) {
            if (actionLink.isVisible()) {
              AnAction action = actionLink.getAction();
              String id = ActionManager.getInstance().getId(action);
              return actionId.equals(id);
            }
            return false;
          }
        });
        if (found.size() == 1) {
          actionLinkRef.set(getFirstItem(found));
          return true;
        }
        return false;
      }
    }, SHORT_TIMEOUT);

    ActionLink actionLink = actionLinkRef.get();
    if (actionLink == null) {
      throw new ComponentLookupException("Failed to find ActionLink with ID '" + actionId + "'");
    }
    return new ActionLinkFixture(robot, actionLink);
  }

  @NotNull
  public static ActionLinkFixture findActionLinkByName(@NotNull final String actionName,
                                                       @NotNull final Robot robot,
                                                       @NotNull final Container container,
                                                       @NotNull final Timeout timeout) {
    final Ref<ActionLink> actionLinkRef = new Ref<ActionLink>();
    pause(new Condition("Find ActionLink with name '" + actionName + "'") {
      @Override
      public boolean test() {
        Collection<ActionLink> found = robot.finder().findAll(container, new GenericTypeMatcher<ActionLink>(ActionLink.class) {
          @Override
          protected boolean isMatching(@NotNull ActionLink actionLink) {
            if (actionLink.isVisible()) {
              return actionLink.getText().equals(actionName);
            }
            return false;
          }
        });
        if (found.size() == 1) {
          actionLinkRef.set(getFirstItem(found));
          return true;
        }
        return false;
      }
    }, timeout);

    ActionLink actionLink = actionLinkRef.get();
    if (actionLink == null) {
      throw new ComponentLookupException("Failed to find ActionLink with name '" + actionName + "'");
    }
    return new ActionLinkFixture(robot, actionLink);
  }

  @NotNull
  public static ActionLinkFixture findActionLinkByName(@NotNull final String actionName,
                                                       @NotNull final Robot robot,
                                                       @NotNull final Container container) {
    return findActionLinkByName(actionName, robot, container, SHORT_TIMEOUT);
  }

  private ActionLinkFixture(@NotNull Robot robot, @NotNull ActionLink target) {
    super(ActionLinkFixture.class, robot, target);
    replaceDriverWith(new ActionLinkDriver(robot));
  }

  static class ActionLinkDriver extends JComponentDriver {

    public ActionLinkDriver(Robot robot) {
      super(robot);
    }

    @Override
    public void click(Component c) {
      clickActionLinkText(c, MouseButton.LEFT_BUTTON, 1);
    }

    @Override
    public void click(Component c, MouseButton button) {
      clickActionLinkText(c, button, 1);
    }

    @Override
    public void click(Component c, MouseClickInfo mouseClickInfo) {
      clickActionLinkText(c, mouseClickInfo.button(), mouseClickInfo.times());
    }

    @Override
    public void doubleClick(Component c) {
      clickActionLinkText(c, MouseButton.LEFT_BUTTON, 2);
    }

    @Override
    public void rightClick(Component c) {
      clickActionLinkText(c, MouseButton.RIGHT_BUTTON, 2);
    }

    @Override
    public void click(Component c, MouseButton button, int times) {
      clickActionLinkText(c, button, times);
    }

    @Override
    public void click(@NotNull Component c, @NotNull Point where) {
      click(c, where, MouseButton.LEFT_BUTTON, 1);
    }

    private void clickActionLinkText(@NotNull Component c, @NotNull MouseButton mouseButton, int times) {
      assert c instanceof ActionLink;
      Point textRectangleCenter = ((ActionLink)c).getTextRectangleCenter();
      click(c, textRectangleCenter, mouseButton, times);
    }

    private void click(@NotNull Component c, @NotNull Point where, MouseButton mouseButton, int times) {
      checkInEdtEnabledAndShowing(c);
      this.robot.click(c, where, mouseButton, times);
    }

  }

}
