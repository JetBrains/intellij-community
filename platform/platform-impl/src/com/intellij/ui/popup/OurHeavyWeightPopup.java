/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui.popup;

import com.intellij.openapi.util.registry.Registry;

import javax.swing.Popup;
import java.awt.Component;
import java.awt.GraphicsEnvironment;

/**
 * @author Sergey Malenkov
 */
public final class OurHeavyWeightPopup extends Popup {
  public OurHeavyWeightPopup(Component owner, Component content, int x, int y) {
    super(owner, content, x, y);
  }

  public static boolean isEnabled() {
    return !GraphicsEnvironment.isHeadless() && Registry.is("our.heavy.weight.popup");
  }
}
