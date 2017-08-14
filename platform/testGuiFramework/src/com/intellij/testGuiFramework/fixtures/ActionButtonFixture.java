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
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.format.Formatting;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class ActionButtonFixture extends JComponentFixture<ActionButtonFixture, ActionButton> {

  @NotNull
  public static ActionButtonFixture findByActionId(@NotNull final String actionId,
                                                   @NotNull final Robot robot,
                                                   @NotNull final Container container, Timeout timeout) {

    final String criteriaDescription = " by actionId: '" + actionId + "'";
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(ActionButton button) {
        AnAction action = button.getAction();
        String buttonActionId = ActionManager.getInstance().getId(action);
        return button.isEnabled() && button.isShowing() && buttonActionId != null && buttonActionId.equals(actionId);
      }
    };
    return getActionButtonFixtureWithMatcher(robot, container, matcher, criteriaDescription, timeout);
  }

  @NotNull
  public static ActionButtonFixture findByActionClassName(@NotNull final String actionClassName,
                                                          @NotNull final Robot robot,
                                                          @NotNull final Container container, Timeout timeout) {
    final String criteriaDescription = " by action class name: '" + actionClassName + "'";
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        AnAction action = button.getAction();
        return button.isShowing()
               && button.isEnabled()
               && action != null
               && action.getClass().getSimpleName().equals(actionClassName);
      }
    };
    return getActionButtonFixtureWithMatcher(robot, container, matcher, criteriaDescription, timeout);
  }

  @NotNull
  private static ActionButtonFixture getActionButtonFixtureWithMatcher(@NotNull Robot robot,
                                                                       @NotNull Container container,
                                                                       @NotNull GenericTypeMatcher<ActionButton> matcher,
                                                                       @NotNull String criteriaDescription,
                                                                       Timeout timeout) {
    final Ref<ActionButton> actionButtonRef = new Ref<ActionButton>();
    Pause.pause(new Condition("Find ActionButton " + criteriaDescription) {
      @Override
      public boolean test() {
        Collection<ActionButton> found = robot.finder().findAll(container, matcher);
        if (found.size() >= 1) {
          ActionButton actionButton = getIfOnce(found, "Find ActionButton " + criteriaDescription);
          if (actionButton == null) return false;
          actionButtonRef.set(actionButton);
          return true;
        }
        return false;
      }
    }, timeout);

    ActionButton button = actionButtonRef.get();
    if (button == null) {
      throw new ComponentLookupException("Failed to find Action button " + criteriaDescription);
    }
    return new ActionButtonFixture(robot, button);
  }

  @Nullable
  private static ActionButton getIfOnce(@NotNull Collection<ActionButton> found, @Nullable String criteria) {
    Stream<ActionButton> objectStream = found.stream();
    if (found.size() > 1) {
      throw new ComponentLookupException("Find more than one ActionButton component matched criteria " + criteria + ": "
                                         + objectStream
                                           .map(component -> Formatting.format((Component)component))
                                           .collect(Collectors.joining(", ")));
    }
    Optional<ActionButton> buttonOptional = objectStream.findFirst();
    return buttonOptional.orElse(null);
  }

  @NotNull
  public static ActionButtonFixture findByActionId(@NotNull final String actionId,
                                                   @NotNull final Robot robot,
                                                   @NotNull final Container container) {
    return findByActionId(actionId, robot, container, SHORT_TIMEOUT);
  }

  @NotNull
  public ActionButtonFixture waitUntilEnabledAndShowing() {
    Pause.pause(new Condition("wait for action to be enabled and showing") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Nullable
          @Override
          protected Boolean executeInEDT() throws Throwable {
            ActionButton target = target();
            if (target.getAction().getTemplatePresentation().isEnabledAndVisible()) {
              return target.isShowing() && target.isVisible() && target.isEnabled();
            }
            return false;
          }
        });
      }
    }, GuiTestUtil.LONG_TIMEOUT);
    return this;
  }

  @NotNull
  public static ActionButtonFixture findByText(@NotNull final String text, @NotNull Robot robot, @NotNull Container container) {
    return findByText(text, robot, container, SHORT_TIMEOUT);
  }

  @NotNull
  public static ActionButtonFixture findByText(@NotNull final String text,
                                               @NotNull Robot robot,
                                               @NotNull Container container,
                                               @NotNull Timeout timeout) {

    String criteria = " by template presentation text: " + text;
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        if (!button.isShowing() || !button.isEnabled()) return false;
        AnAction action = button.getAction();
        return text.equals(action.getTemplatePresentation().getText());
      }
    };
    return getActionButtonFixtureWithMatcher(robot, container, matcher, criteria, timeout);
  }

  private ActionButtonFixture(@NotNull Robot robot, @NotNull ActionButton target) {
    super(ActionButtonFixture.class, robot, target);
  }
}
