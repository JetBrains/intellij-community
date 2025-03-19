// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * @author anna
 */
public abstract class AbstractTestProxy extends CompositePrintable {
  public static final DataKey<AbstractTestProxy> DATA_KEY = DataKey.create("testProxy");
  public static final DataKey<AbstractTestProxy[]> DATA_KEYS = DataKey.create("testProxies");

  protected Printer myPrinter = null;

  public abstract boolean isInProgress();

  public abstract boolean isDefect();

  public abstract boolean shouldRun();

  public abstract int getMagnitude();

  public abstract boolean isLeaf();

  public abstract boolean isInterrupted();

  public abstract boolean hasPassedTests();

  public abstract boolean isIgnored();

  public abstract boolean isPassed();

  public abstract String getName();

  public abstract boolean isConfig();

  public abstract Location getLocation(@NotNull Project project, @NotNull GlobalSearchScope searchScope);

  public abstract Navigatable getDescriptor(@Nullable Location location, @NotNull TestConsoleProperties properties);

  public abstract AbstractTestProxy getParent();

  public abstract List<? extends AbstractTestProxy> getChildren();

  public abstract List<? extends AbstractTestProxy> getAllTests();

  public @Nullable Long getDuration() {
    return null;
  }

  /**
   * Retrieves the customized duration for the test, if available.
   * Must be consistent with {@link AbstractTestProxy#getDurationString(TestConsoleProperties)}, because it is a value,
   * which users see in UI
   * Used to show customized raw values (for example, for reports)
   * @see TestResultsXmlFormatter
   *
   * @param testConsoleProperties the console properties used to configure test behavior and output
   * @return the duration as a {@link Long} if available, or {@code null} if not specified,
   * default value is {@link AbstractTestProxy#getDuration()}
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public @Nullable Long getCustomizedDuration(@NotNull TestConsoleProperties testConsoleProperties) {
    return getDuration();
  }

  @Nls
  public @Nullable String getDurationString(TestConsoleProperties consoleProperties) {
    return null;
  }

  public abstract boolean shouldSkipRootNodeForExport();

  protected void fireOnNewPrintable(final @NotNull Printable printable) {
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
  public void addLast(final @NotNull Printable printable) {
    super.addLast(printable);
    fireOnNewPrintable(printable);
  }

  @Override
  public void insert(final @NotNull Printable printable, int i) {
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

  @Override
  public int getExceptionMark() {
    if (myExceptionMark == 0 && !getChildren().isEmpty()) {
      return getChildren().get(0).getExceptionMark();
    }
    return myExceptionMark;
  }

  public @NotNull @Unmodifiable List<DiffHyperlink> getDiffViewerProviders() {
    final DiffHyperlink provider = getDiffViewerProvider();
    return ContainerUtil.createMaybeSingletonList(provider);
  }

  public @Nullable String getStacktrace() {
    return null;
  }
  
  public @Nullable DiffHyperlink getLeafDiffViewerProvider() {
    DiffHyperlink provider = getDiffViewerProvider();
    if (provider != null) return provider;
    if (isDefect()) {
      for (AbstractTestProxy child : getChildren()) {
        provider = child.getLeafDiffViewerProvider();
        if (provider != null) return provider;
      }
    }
    return null;
  }

  public @Nullable DiffHyperlink getDiffViewerProvider() {
    return null;
  }

  @Override
  public DiffHyperlink createHyperlink(String expected,
                                          String actual,
                                          String filePath,
                                          final String actualFilePath, final boolean printOneLine) {
    DiffHyperlink hyperlink = super.createHyperlink(expected, actual, filePath, actualFilePath, printOneLine);
    hyperlink.setTestProxy(this);
    return hyperlink;
  }

  public @Nullable String getLocationUrl() {
    return null;
  }

  public @Nullable String getMetainfo() {
    return null;
  }

  public @Nullable String getErrorMessage() {
    return null;
  }

  public static @Nullable TestProxyRoot getTestRoot(@NotNull AbstractTestProxy proxy) {
    if (proxy instanceof TestProxyRoot) {
      return (TestProxyRoot)proxy;
    }
    AbstractTestProxy parent = proxy.getParent();
    while (parent != null && !(parent instanceof TestProxyRoot)) {
      parent = parent.getParent();
    }
    return parent != null ? (TestProxyRoot)parent : null;
  }
}
