// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

public class IdeaWriteEvent {
  private final String myText;

  public IdeaWriteEvent(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }
}
