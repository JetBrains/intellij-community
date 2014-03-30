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
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public abstract boolean isIgnored();

  public abstract boolean isPassed();

  public abstract String getName();

  public abstract Location getLocation(final Project project, GlobalSearchScope searchScope);

  public abstract Navigatable getDescriptor(final Location location, final TestConsoleProperties testConsoleProperties);

  public abstract AbstractTestProxy getParent();

  public abstract List<? extends AbstractTestProxy> getChildren();

  public abstract List<? extends AbstractTestProxy> getAllTests();

  @Nullable
  public Long getDuration() {
    return null;
  }

  public abstract boolean shouldSkipRootNodeForExport();

  public void fireOnNewPrintable(@NotNull final Printable printable) {
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
  public void addLast(@NotNull final Printable printable) {
    super.addLast(printable);
    fireOnNewPrintable(printable);
  }

  @Override
  public void insert(@NotNull final Printable printable, int i) {
    super.insert(printable, i);
    fireOnNewPrintable(printable);
  }

  @Override
  public void dispose() {
    super.dispose();
    for (AbstractTestProxy proxy : getChildren()) {
      Disposer.dispose(proxy);
    }
  }

  public static void flushOutput(AbstractTestProxy testProxy) {
    testProxy.flush();

    AbstractTestProxy parent = testProxy.getParent();
    while (parent != null) {
      final List<? extends AbstractTestProxy> children = parent.getChildren();
      if (!testProxy.isInProgress() && testProxy.equals(children.get(children.size() - 1))) {
        parent.flush();
      } else {
        break;
      }
      testProxy = parent;
      parent = parent.getParent();
    }
  }

  @Override
  public int getExceptionMark() {
    if (myExceptionMark == 0 && getChildren().size() > 0) {
      return getChildren().get(0).getExceptionMark();
    }
    return myExceptionMark;
  }

  @Nullable
  public AssertEqualsDiffViewerProvider getDiffViewerProvider() {
    return null;
  }

  public interface AssertEqualsDiffViewerProvider {
    void openDiff(final Project project);
    String getExpected();
    String getActual();
  }
}
