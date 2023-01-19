// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleConsoleProperties;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationCustomizer;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationCustomizer.GradleTestLocationInfo;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractTestEvent implements TestEvent {
  private final GradleTestsExecutionConsole myExecutionConsole;

  public AbstractTestEvent(GradleTestsExecutionConsole executionConsole) {
    this.myExecutionConsole = executionConsole;
  }

  public GradleTestsExecutionConsole getExecutionConsole() {
    return myExecutionConsole;
  }

  protected SMTestRunnerResultsForm getResultsViewer() {
    return myExecutionConsole.getResultsViewer();
  }

  protected Project getProject() {
    return myExecutionConsole.getProperties().getProject();
  }

  protected GradleConsoleProperties getProperties() {
    return (GradleConsoleProperties)getExecutionConsole().getProperties();
  }

  @NotNull
  protected String findLocationUrl(@Nullable String name, @NotNull String fqClassName) {
    return findLocationUrl(JavaTestLocator.TEST_PROTOCOL, name, fqClassName);
  }

  @NotNull
  protected static String findLocationUrl(@NotNull String protocol, @Nullable String name, @NotNull String fqClassName) {
    return name == null
           ? JavaTestLocator.createLocationUrl(protocol, fqClassName)
           : JavaTestLocator.createLocationUrl(protocol, fqClassName, name);
  }

  @Nullable
  protected SMTestProxy findTestProxy(final String proxyId) {
    return getExecutionConsole().getTestsMap().get(proxyId);
  }

  protected void registerTestProxy(final String proxyId, SMTestProxy testProxy) {
    myExecutionConsole.getTestsMap().put(proxyId, testProxy);
  }

  protected String decode(String s) {
    return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
  }

  protected boolean showInternalTestNodes() {
    return GradleConsoleProperties.SHOW_INTERNAL_TEST_NODES.value(getProperties());
  }

  protected final @NotNull String computeLocationUrl(@Nullable SMTestProxy parentProxy, @NotNull String fqClassName, @Nullable String name, @Nullable String displayName) {
    if (parentProxy != null) {
      for (GradleTestLocationCustomizer customizer : GradleTestLocationCustomizer.EP_NAME.getExtensionList()) {
        GradleTestLocationInfo location = customizer.getLocationInfo(getProject(), parentProxy, fqClassName, name, displayName);
        if (location != null) {
          return findLocationUrl(location.getMethodName(), location.getFqClassName());
        }
      }
    }
    return findLocationUrl(name, fqClassName);
  }
}
