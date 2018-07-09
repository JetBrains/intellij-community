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

import com.intellij.testGuiFramework.driver.CheckBoxDriver;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.components.JBCheckBox;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComponentDriver;
import org.fest.swing.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author Sergey Karashevich
 */
public class JBCheckBoxFixture extends JComponentFixture<JBCheckBoxFixture, JBCheckBox> {

  private final Robot myRobot;
  private final JBCheckBox myCheckBox;

  public JBCheckBoxFixture(@NotNull Class<JBCheckBoxFixture> selfType,
                           @NotNull Robot robot,
                           @NotNull JBCheckBox target) {
    super(selfType, robot, target);
    myRobot = robot;
    myCheckBox = target;
  }


  public static JBCheckBoxFixture findByText(@NotNull String text, @Nullable Container root, @NotNull Robot robot, boolean waitUntilFound) {

    GenericTypeMatcher<JBCheckBox> matcher = new GenericTypeMatcher<JBCheckBox>(JBCheckBox.class) {
      @Override
      protected boolean isMatching(@Nonnull JBCheckBox box) {
        return (box.getText() != null && box.getText().toLowerCase().equals(text.toLowerCase()));
      }
    };
    return findWithGenericTypeMatcher(matcher, root, robot, waitUntilFound);
  }

  //Attention: could be found more than one instance of JBCheckBox!
  public static JBCheckBoxFixture findByPartOfText(@NotNull String partOfText, Container root, @NotNull Robot robot, boolean waitUntilFound) {

    GenericTypeMatcher<JBCheckBox> matcher = new GenericTypeMatcher<JBCheckBox>(JBCheckBox.class) {
      @Override
      protected boolean isMatching(@Nonnull JBCheckBox box) {
        return (box.getText() != null && box.getText().toLowerCase().contains(partOfText.toLowerCase()));
      }
    };

    return findWithGenericTypeMatcher(matcher, root, robot, waitUntilFound);
  }

  public static JBCheckBoxFixture findWithGenericTypeMatcher(@NotNull GenericTypeMatcher matcher, @Nullable Container root, @NotNull Robot robot, boolean waitUntilFound) {
    if (waitUntilFound) {
      Component component = GuiTestUtil.INSTANCE.waitUntilFound(robot, root, matcher);
      assert (component instanceof JBCheckBox);
      return new JBCheckBoxFixture(JBCheckBoxFixture.class, robot, (JBCheckBox)component);
    } else {
      Component component = robot.finder().find(matcher);
      if (component == null) throw new ComponentLookupException("JBCheckBox with matcher hasn't been found");
      assert (component instanceof JBCheckBox);
      return new JBCheckBoxFixture(JBCheckBoxFixture.class, robot, (JBCheckBox)component);
    }
  }


  @NotNull
  @Override
  protected JComponentDriver createDriver(@NotNull Robot robot) {
    return new CheckBoxDriver(robot);
  }

}

