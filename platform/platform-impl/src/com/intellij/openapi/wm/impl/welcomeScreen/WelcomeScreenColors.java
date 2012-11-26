/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import java.awt.*;

public class WelcomeScreenColors {

  // These two for the topmost "Welcome to <product name>"
  static final Color WELCOME_HEADER_BACKGROUND = new JBColor(Gray._220, Gray._75);
  static final Color WELCOME_HEADER_FOREGROUND = new JBColor(Gray._80, Gray._197);

  // This is for border around recent projects, action cards and also lines separating header and footer from main contents.
  static final Color BORDER_COLOR = new JBColor(Gray._190, Gray._85);

  // This is for circle around hovered (next) icon
  static final Color GROUP_ICON_BORDER_COLOR = new JBColor(Gray._190, Gray._55);

  // These two are for footer (Full product with build #, small letters)
  static final Color FOOTER_BACKGROUND = new JBColor(Gray._210, Gray._75);
  static final Color FOOTER_FOREGROUND = new JBColor(Color.black, Gray._197);

  // There two are for caption of Recent Project and Action Cards
  static final Color CAPTION_BACKGROUND = new JBColor(Gray._210, Gray._75);
  static final Color CAPTION_FOREGROUND = new JBColor(Color.black, Gray._197);

  private WelcomeScreenColors() {}
}
