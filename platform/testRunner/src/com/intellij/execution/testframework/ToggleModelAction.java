/*
 * User: anna
 * Date: 28-Aug-2009
 */
package com.intellij.execution.testframework;

import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.ToggleBooleanProperty;

import javax.swing.*;

public abstract class ToggleModelAction extends ToggleBooleanProperty.Disablable {
  public ToggleModelAction(String text, String description, Icon icon, AbstractProperty.AbstractPropertyContainer properties,
                           BooleanProperty property) {
    super(text, description, icon, properties, property);
  }

  public abstract void setModel(TestFrameworkRunningModel model);

}