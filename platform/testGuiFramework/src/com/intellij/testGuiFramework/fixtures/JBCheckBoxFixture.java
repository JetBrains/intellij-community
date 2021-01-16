// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testGuiFramework.driver.CheckBoxDriver;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.components.JBCheckBox;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComponentDriver;
import org.fest.swing.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    GenericTypeMatcher<JBCheckBox> matcher = new GenericTypeMatcher<>(JBCheckBox.class) {
      @Override
      protected boolean isMatching(@NotNull JBCheckBox box) {
        return (box.getText() != null && StringUtil.toLowerCase(box.getText()).equals(StringUtil.toLowerCase(text)));
      }
    };
    return findWithGenericTypeMatcher(matcher, root, robot, waitUntilFound);
  }

  //Attention: could be found more than one instance of JBCheckBox!
  public static JBCheckBoxFixture findByPartOfText(@NotNull String partOfText, Container root, @NotNull Robot robot, boolean waitUntilFound) {

    GenericTypeMatcher<JBCheckBox> matcher = new GenericTypeMatcher<>(JBCheckBox.class) {
      @Override
      protected boolean isMatching(@NotNull JBCheckBox box) {
        return (box.getText() != null && StringUtil.toLowerCase(box.getText()).contains(StringUtil.toLowerCase(partOfText)));
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

