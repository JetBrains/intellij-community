/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.ui;

import java.awt.Color;

/**
 * @author max
 */
public interface LightColors {
  Color YELLOW = new JBColor(new Color(0xffffcc), new Color(0x525229));
  Color GREEN = new JBColor(new Color(0xccffcc), new Color(0x356936));
  Color BLUE = new JBColor(new Color(0xccccff), new Color(0x589df6));
  Color RED = new JBColor(new Color(0xffcccc), new Color(0x743A3A));
  Color CYAN = new JBColor(new Color(0xccffff), new Color(100, 138, 138));

  Color SLIGHTLY_GREEN = new JBColor(new Color(0xeeffee), new Color(0x515B51));
  Color SLIGHTLY_GRAY = new JBColor(new Color(0xf5f5f5), new Color(0xc0c0c0));
}
