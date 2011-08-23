/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.ToolWindowAnchor;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/19/11
 * Time: 3:49 PM
 */
public abstract class AnchoredButton extends JToggleButton {
  protected AnchoredButton(String text, Icon icon, boolean selected) {
    super(text, icon, selected);
  }

  protected AnchoredButton(String text, Icon icon) {
    super(text, icon);
  }

  protected AnchoredButton(Action a) {
    super(a);
  }

  protected AnchoredButton(String text, boolean selected) {
    super(text, selected);
  }

  protected AnchoredButton(String text) {
    super(text);
  }

  protected AnchoredButton(Icon icon, boolean selected) {
    super(icon, selected);
  }

  protected AnchoredButton(Icon icon) {
    super(icon);
  }

  protected AnchoredButton() {
  }

  public abstract int getMnemonic2();
  public abstract ToolWindowAnchor getAnchor();
}
