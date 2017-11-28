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
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.AbstractButtonDriver;
import org.fest.swing.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey Karashevich
 */
public class CheckBoxFixture extends org.fest.swing.fixture.JCheckBoxFixture {

  public CheckBoxFixture(@Nonnull Robot robot, @Nonnull JCheckBox target) {
    super(robot, target);
  }


  public static CheckBoxFixture findByText(@NotNull String text, @Nullable Container root, @NotNull Robot robot, boolean waitUntilFound){
    GenericTypeMatcher<JCheckBox> matcher = new GenericTypeMatcher<JCheckBox>(JCheckBox.class) {

      @Override
      protected boolean isMatching(@Nonnull JCheckBox box) {
        return (box.getText() != null && box.getText().toLowerCase().equals(text.toLowerCase()));
      }
    };
    return findWithGenericTypeMatcher(matcher, root, robot, waitUntilFound);
  }

  public static CheckBoxFixture findWithGenericTypeMatcher(@NotNull GenericTypeMatcher matcher, @Nullable Container root, @NotNull Robot robot, boolean waitUntilFound) {
    Component component;
    if (waitUntilFound) {
      component = GuiTestUtil.waitUntilFound(robot, root, matcher);
    } else {
      component = robot.finder().find(matcher);
      if (component == null) throw new ComponentLookupException("JBCheckBox with matcher hasn't been found");
    }
    assert (component instanceof JCheckBox);
    return new CheckBoxFixture(robot, (JCheckBox)component);
  }

  //checks a status of the checkbox
  public boolean isSelected() {
    return target().isSelected();
  }


  @NotNull
  @Override
  protected AbstractButtonDriver createDriver(@NotNull Robot robot) {
    return new CheckBoxDriver(robot);
  }
}
