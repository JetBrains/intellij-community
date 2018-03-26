/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 2/25/14
 */
public class GradleSMTestProxy extends SMTestProxy {

  @Nullable private final String myClassName;
  @Nullable private String myParentId;

  public GradleSMTestProxy(String testName, boolean isSuite, @Nullable String locationUrl, @Nullable String className) {
    super(testName, isSuite, locationUrl);
    myClassName = className;
  }

  @Override
  public void addStdOutput(String output, Key outputType) {
    addLast(new Printable() {
      public void printOn(final Printer printer) {
        ConsoleViewContentType contentType = ConsoleViewContentType.getConsoleViewType(outputType);
        if (ConsoleViewContentType.NORMAL_OUTPUT.equals(contentType)) {
          printer.printWithAnsiColoring(output, contentType);
        }
        else {
          printer.print(output, contentType);
        }
      }
    });
  }

  @Nullable
  public String getParentId() {
    return myParentId;
  }

  public void setParentId(@Nullable String parentId) {
    myParentId = parentId;
  }

  @Nullable
  public String getClassName() {
    return myClassName;
  }
}
