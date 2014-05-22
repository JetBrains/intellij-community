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

package com.intellij.vcs.log.graph.impl.print.elements;

import com.intellij.vcs.log.graph.SimplePrintElement;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import org.jetbrains.annotations.NotNull;

public class SimplePrintElementImpl extends AbstractPrintElement implements SimplePrintElement {

  @NotNull
  private final Type myType;

  public SimplePrintElementImpl(int rowIndex,
                                int positionInCurrentRow,
                                @NotNull Type type,
                                @NotNull GraphElement graphElement,
                                @NotNull PrintElementsManager printElementsManager) {
    super(rowIndex, positionInCurrentRow, graphElement, printElementsManager);
    myType = type;
  }

  @NotNull
  @Override
  public Type getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimplePrintElement)) return false;

    SimplePrintElement that = (SimplePrintElement)o;

    if (myPositionInCurrentRow != that.getPositionInCurrentRow()) return false;
    if (myRowIndex != that.getRowIndex()) return false;
    if (myType != that.getType()) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRowIndex;
    result = 31 * result + myPositionInCurrentRow;
    result = 37 * result + myType.hashCode();
    return result;
  }

}
