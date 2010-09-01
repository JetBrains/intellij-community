/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @Override
  protected boolean isVisible() {
    return true;
  }

  public abstract void setModel(TestFrameworkRunningModel model);

}