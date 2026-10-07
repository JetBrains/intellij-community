// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.impl.ExtensionPointImplTest;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Several applications in one JVM (language server, tests) share static query factories:
 * {@link ExtensibleQueryFactory} must follow the EP its supplier returns now, not the first one.
 */
public class ExtensibleQueryFactoryTest {
  private static final String EP_NAME = "test.extensibleQueryFactory";

  private final Disposable disposable = Disposer.newDisposable();

  @After
  public void tearDown() {
    Disposer.dispose(disposable);
  }

  @Test
  public void returnsExecutorsOfCurrentPointAfterSwitch() {
    ExtensionPoint<QueryExecutor<String, String>> first = createPoint(executor("a"));
    ExtensionPoint<QueryExecutor<String, String>> second = createPoint(executor("b"));
    AtomicReference<ExtensionPoint<QueryExecutor<String, String>>> current = new AtomicReference<>(first);
    ExtensibleQueryFactory<String, String> factory = new ExtensibleQueryFactory<>(current::get);

    assertThat(names(factory)).containsExactly("a");

    current.set(second);
    assertThat(names(factory)).containsExactly("b");

    current.set(first);
    assertThat(names(factory)).containsExactly("a");
  }

  @Test
  public void seesExtensionsChangedOnCurrentPoint() {
    ExtensionPoint<QueryExecutor<String, String>> point = createPoint(executor("a"));
    ExtensibleQueryFactory<String, String> factory = new ExtensibleQueryFactory<>(() -> point);

    Disposable extensionDisposable = Disposer.newDisposable(disposable);
    point.registerExtension(executor("b"), extensionDisposable);
    assertThat(names(factory)).containsExactly("a", "b");

    Disposer.dispose(extensionDisposable);
    assertThat(names(factory)).containsExactly("a");
  }

  @Test
  public void putsExplicitExecutorsFirstOnEachPoint() {
    ExtensionPoint<QueryExecutor<String, String>> first = createPoint(executor("a"));
    ExtensionPoint<QueryExecutor<String, String>> second = createPoint(executor("b"));
    AtomicReference<ExtensionPoint<QueryExecutor<String, String>>> current = new AtomicReference<>(first);
    ExtensibleQueryFactory<String, String> factory = new ExtensibleQueryFactory<>(current::get);
    Disposable executorDisposable = Disposer.newDisposable(disposable);
    factory.registerExecutor(executor("x"), executorDisposable);

    assertThat(names(factory)).containsExactly("x", "a");

    current.set(second);
    assertThat(names(factory)).containsExactly("x", "b");

    Disposer.dispose(executorDisposable);
    assertThat(names(factory)).containsExactly("b");
  }

  private static @NotNull QueryExecutor<String, String> executor(@NotNull String name) {
    return new NamedExecutor(name);
  }

  /**
   * Running a query needs an application, so tests check the executor list instead.
   */
  private static @NotNull List<String> names(@NotNull ExtensibleQueryFactory<String, String> factory) {
    return ContainerUtil.map(factory.getExecutors(), executor -> ((NamedExecutor)executor).name);
  }

  private record NamedExecutor(@NotNull String name) implements QueryExecutor<String, String> {
    @Override
    public boolean execute(@NotNull String queryParameters, @NotNull Processor<? super String> consumer) {
      return consumer.process(name);
    }
  }

  private @NotNull ExtensionPoint<QueryExecutor<String, String>> createPoint(@NotNull QueryExecutor<String, String> executor) {
    ExtensionsAreaImpl area = new ExtensionsAreaImpl(new ExtensionPointImplTest.MyComponentManager());
    area.registerExtensionPoint(EP_NAME, QueryExecutor.class.getName(), ExtensionPoint.Kind.INTERFACE, false);
    ExtensionPoint<QueryExecutor<String, String>> point = area.getExtensionPoint(EP_NAME);
    point.registerExtension(executor, disposable);
    return point;
  }
}
