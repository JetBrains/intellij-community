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
 * User: anna
 * Date: 23-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;

import java.util.List;

public abstract class AbstractTestProxy extends CompositePrintable {
  public static final DataKey<AbstractTestProxy> DATA_KEY = DataKey.create("testProxy");
  protected Printer myPrinter = null;

  public abstract boolean isInProgress();

  public abstract boolean isDefect();

  //todo?
  public abstract boolean shouldRun();

  public abstract int getMagnitude();

  public abstract boolean isLeaf();

  public abstract boolean isInterrupted();

  public abstract boolean isPassed();

  public abstract String getName();

  public abstract Location getLocation(final Project project);

  public abstract Navigatable getDescriptor(final Location location);

  public abstract AbstractTestProxy getParent();

  public abstract List<? extends AbstractTestProxy> getChildren();

  public abstract List<? extends AbstractTestProxy> getAllTests();

  public void fireOnNewPrintable(final Printable printable) {
    if (myPrinter != null) {
      myPrinter.onNewAvailable(printable);
    }
  }

  public void setPrinter(final Printer printer) {
    myPrinter = printer;
    for (AbstractTestProxy testProxy : getChildren()) {
      testProxy.setPrinter(printer);
    }
  }

  /**
   * Stores printable information in internal buffer and notifies
   * proxy's printer about new text available
   * @param printable Printable info
   */
  @Override
  public void addLast(final Printable printable) {
    super.addLast(printable);
    fireOnNewPrintable(printable);
  }

  @Override
  public void dispose() {
    super.dispose();
    for (AbstractTestProxy proxy : getChildren()) {
      Disposer.dispose(proxy);
    }
  }
}
