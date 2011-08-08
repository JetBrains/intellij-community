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
package com.intellij.openapi.vcs;

import com.intellij.ui.SimpleTextAttributes;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/3/11
 * Time: 12:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class RichTextItem {
  private final String myText;
  private final SimpleTextAttributes myTextAttributes;

  public RichTextItem(String text, SimpleTextAttributes textAttributes) {
    myText = text;
    myTextAttributes = textAttributes;
  }

  public String getText() {
    return myText;
  }

  public SimpleTextAttributes getTextAttributes() {
    return myTextAttributes;
  }
}
