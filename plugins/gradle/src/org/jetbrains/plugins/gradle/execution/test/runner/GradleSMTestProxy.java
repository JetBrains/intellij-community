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

import com.intellij.execution.Location;
import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 2/25/14
 */
public class GradleSMTestProxy extends SMTestProxy {

  @Nullable private final String myClassName;
  @Nullable private String myStacktrace;
  @Nullable private String myParentId;

  public GradleSMTestProxy(String testName, boolean isSuite, @Nullable String locationUrl, @Nullable String className) {
    super(testName, isSuite, locationUrl);
    myClassName = className;
  }

  @Override
  public void setTestFailed(@NotNull String localizedMessage, @Nullable String stackTrace, boolean testError) {
    setStacktraceIfNotSet(stackTrace);
    super.setTestFailed(localizedMessage, stackTrace, testError);
  }

  @Override
  public void setTestComparisonFailed(@NotNull String localizedMessage,
                                      @Nullable String stackTrace,
                                      @NotNull String actualText,
                                      @NotNull String expectedText) {
    setStacktraceIfNotSet(stackTrace);
    super.setTestComparisonFailed(localizedMessage, stackTrace, actualText, expectedText);
  }

  @Override
  public void setTestIgnored(@Nullable String ignoreComment, @Nullable String stackTrace) {
    setStacktraceIfNotSet(stackTrace);
    super.setTestIgnored(ignoreComment, stackTrace);
  }

  @Nullable
  @Override
  public Location getLocation(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    if (getLocationUrl() != null) {
      if (isDefect() && myStacktrace != null) {
        final String[] stackTrace = new LineTokenizer(myStacktrace).execute();
        for (String aStackTrace : stackTrace) {
          final StackTraceLine line = new StackTraceLine(project, aStackTrace);
          if (getName().equals(line.getMethodName()) && StringUtil.equals(myClassName, line.getClassName())) {
            return line.getMethodLocation(project);
          }
        }
      }
    }

    return super.getLocation(project, searchScope);
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

  private void setStacktraceIfNotSet(@Nullable String stacktrace) {
    if (myStacktrace == null) myStacktrace = stacktrace;
  }
}
