/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

public class TextRevisionNumber implements VcsRevisionNumber {
  private final String myText;

  public TextRevisionNumber(final String text) {
    myText = text;
  }

  public String asString() {
    return myText;
  }

  public int compareTo(final VcsRevisionNumber o) {
    return Comparing.compare(myText, ((TextRevisionNumber) o).myText);
  }
}
