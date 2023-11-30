// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.collectionUtil;

import org.gradle.api.DomainObjectCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@ApiStatus.Experimental
public class GradleCollectionVisitorImpl<T> implements GradleCollectionVisitor<T> {

  private final @NotNull DomainObjectCollection<T> domainObjectCollection;
  private final @NotNull Consumer<T> objectCollector;

  private @Nullable BiConsumer<T, Exception> failureHandler = null;
  private @Nullable BiConsumer<T, Exception> elementSkipHandler = null;

  public GradleCollectionVisitorImpl(@NotNull DomainObjectCollection<T> collection, @NotNull Consumer<T> collector) {
    domainObjectCollection = collection;
    objectCollector = collector;
  }

  @Override
  public @NotNull GradleCollectionVisitorImpl<T> onFailure(@NotNull BiConsumer<T, Exception> handler) {
    failureHandler = handler;
    return this;
  }

  @Override
  public @NotNull GradleCollectionVisitorImpl<T> onElementSkip(@NotNull BiConsumer<T, Exception> handler) {
    elementSkipHandler = handler;
    return this;
  }

  @Override
  public void accept() {
    AtomicBoolean isCollected = new AtomicBoolean(false);
    domainObjectCollection.all(object -> {
      if (isCollected.get() && elementSkipHandler != null) {
        IllegalStateException stackTrace = new IllegalStateException();
        elementSkipHandler.accept(object, stackTrace);
      }
      try {
        objectCollector.accept(object);
      }
      catch (Exception exception) {
        if (failureHandler != null) {
          failureHandler.accept(object, exception);
        }
      }
    });
    isCollected.set(true);
  }
}
