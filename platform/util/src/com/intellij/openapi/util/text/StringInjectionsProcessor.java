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
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.NotNull;

/**
 * @author Irina.Chernushina on 8/22/2016.
 */
public abstract class StringInjectionsProcessor {
  @NotNull
  private final String myStart;
  @NotNull
  private final String myEnd;
  @NotNull
  private final String myText;

  public StringInjectionsProcessor(@NotNull String start, @NotNull String end, @NotNull String text) {
    myStart = start;
    myEnd = end;
    myText = text;
  }

  public void process() {
    int currentPos = 0;
    while (currentPos < myText.length()) {
      final int startInjection = myText.indexOf(myStart, currentPos);
      if (startInjection < 0) {
        onText(myText.substring(currentPos));
        return;
      }
      int afterStart = startInjection + myStart.length();

      int endInjection = InjectorMatchingEndFinder.findMatchingEnd(myStart, myEnd, myText, afterStart);
      if (endInjection < 0) {
        onInjection(myText.substring(currentPos));
        return;
      }
      if(!onText(myText.substring(currentPos, startInjection))) return;
      if(!onInjection(myText.substring(startInjection, endInjection + myEnd.length()))) return;
      currentPos = endInjection + myEnd.length();
    }
  }

  protected abstract boolean onText(@NotNull final String text);
  protected abstract boolean onInjection(@NotNull final String injection);
}
