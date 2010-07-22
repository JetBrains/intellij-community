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
package com.intellij.execution.testframework;

import java.util.ArrayList;

import java.util.List;

public class CompositePrintable implements Printable {
  public static final String NEW_LINE = "\n";

  protected final ArrayList<Printable> myNestedPrintables = new ArrayList<Printable>();

  public void printOn(final Printer printer) {
    printAllOn(myNestedPrintables, printer);
  }

  public void addLast(final Printable printable) {
    myNestedPrintables.add(printable);
  }

  protected void clear() {
    myNestedPrintables.clear();
  }

  public static <T extends Printable> void printAllOn(final List<T> printables, final Printer console) {
    for (final T printable : printables) {
      printable.printOn(console);
    }
  }
}

