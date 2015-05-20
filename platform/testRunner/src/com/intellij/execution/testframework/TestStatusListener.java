/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public abstract class TestStatusListener {
  public static final ExtensionPointName<TestStatusListener> EP_NAME = ExtensionPointName.create("com.intellij.testStatusListener");

  public abstract void testSuiteFinished(@Nullable AbstractTestProxy root);

  public void testSuiteFinished(@Nullable AbstractTestProxy root, Project project) {
    testSuiteFinished(root);
  }

  @Deprecated
  @SuppressWarnings("UnusedDeclaration")
  public static void notifySuiteFinished(AbstractTestProxy root) {
    for (TestStatusListener statusListener : Extensions.getExtensions(EP_NAME)) {
      statusListener.testSuiteFinished(root);
    }
  }

  public static void notifySuiteFinished(@Nullable AbstractTestProxy root, Project project) {
    for (TestStatusListener statusListener : Extensions.getExtensions(EP_NAME)) {
      statusListener.testSuiteFinished(root, project);
    }
  }
}
