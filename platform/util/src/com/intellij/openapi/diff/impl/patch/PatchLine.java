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
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 20:04:24
 */
package com.intellij.openapi.diff.impl.patch;

public class PatchLine {
  public enum Type { CONTEXT, ADD, REMOVE }

  private final Type myType;
  private final String myText;
  private boolean mySuppressNewLine;

  public PatchLine(final Type type, final String text) {
    myType = type;
    myText = text;
  }

  public Type getType() {
    return myType;
  }

  public String getText() {
    return myText;
  }

  public boolean isSuppressNewLine() {
    return mySuppressNewLine;
  }

  public void setSuppressNewLine(final boolean suppressNewLine) {
    mySuppressNewLine = suppressNewLine;
  }

  @Override
  public String toString() {
    return "PatchLine{" +
           "myType=" + myType +
           ", myText='" + myText + '\'' +
           ", mySuppressNewLine=" + mySuppressNewLine +
           '}';
  }
}
