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
package com.intellij.vcs.log.newgraph.render.cell;

import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import org.jetbrains.annotations.NotNull;

public class SpecialRowElement {
  @NotNull
  private final GraphElement myElement;

  private final int myPosition;

  @NotNull
  private final Type myType;

  public SpecialRowElement(@NotNull GraphElement element, int position, @NotNull Type type) {
    myElement = element;
    myPosition = position;
    myType = type;
  }

  @NotNull
  public GraphElement getElement() {
    return myElement;
  }

  public int getPosition() {
    return myPosition;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SpecialRowElement)) return false;

    SpecialRowElement element = (SpecialRowElement)o;

    if (myPosition != element.myPosition) return false;
    if (!myElement.equals(element.myElement)) return false;
    if (myType != element.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myElement.hashCode();
    result = 31 * result + myPosition;
    result = 31 * result + myType.hashCode();
    return result;
  }

  public enum Type {
    NODE,
    UP_ARROW,
    DOWN_ARROW,

    DOWN_HARMONICA,
    UP_HARMONICA
  }

}
