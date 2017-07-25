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
package com.intellij.testGuiFramework.cellReader;

import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.swing.cell.JListCellReader;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.driver.BasicJListCellReader;
import org.fest.swing.exception.ComponentLookupException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Sergey Karashevich
 */
public class ExtendedJListCellReader extends BasicJListCellReader implements JListCellReader {

  public ExtendedJListCellReader() {
    super();
  }

  @Nullable
  @Override
  public String valueAt(@Nonnull JList list, int index) {

    Object element = list.getModel().getElementAt(index);
    Component cellRenComp = GuiTestUtil.getListCellRendererComponent(list, element, index);
    if (cellRenComp instanceof JLabel) {
      return ((JLabel)cellRenComp).getText();
    }
    else if (cellRenComp instanceof JPanel) {
      try {
        Component foundComp =
          BasicRobot.robotWithNewAwtHierarchyWithoutScreenLock().finder().find((JPanel)cellRenComp, component -> component instanceof JLabel && ((JLabel)component).getText() != null && !((JLabel)component).getText().isEmpty());
        return ((JLabel)foundComp).getText();
      }
      catch (ComponentLookupException ignored) {
      }
    }

    Method getNameMethod = null;
    try {
      getNameMethod = element.getClass().getMethod("getName");
      Object result = getNameMethod.invoke(element);
      assert (result instanceof String);
      return (String)result;
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      try {
        getNameMethod = element.getClass().getMethod("getText");
        Object result = getNameMethod.invoke(element);
        assert (result instanceof String);
        return (String)result;
      }
      catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e1) {
        return null;
      }
    }
  }
}
