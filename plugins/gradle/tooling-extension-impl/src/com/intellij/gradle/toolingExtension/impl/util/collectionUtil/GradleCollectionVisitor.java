// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.collectionUtil;

import org.gradle.api.DomainObjectCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@ApiStatus.Experimental
public interface GradleCollectionVisitor<T> {

  /**
   * Sets the failure handler for the Gradle collection visitor.
   * This handler is invoked when an exception occurs during the collection process.
   *
   * @return The GradleCollectionVisitor with the failure handler set.
   */
  @CheckReturnValue
  @NotNull GradleCollectionVisitor<T> onFailure(@NotNull BiConsumer<T, Exception> handler);

  /**
   * Sets the handler of skipped element for the Gradle collection visitor.
   * This handler is invoked when an element registers in a collection when the collecting process is finished.
   *
   * @return The GradleCollectionVisitor with the element skip handler set.
   */
  @CheckReturnValue
  @NotNull GradleCollectionVisitor<T> onElementSkip(@NotNull BiConsumer<T, Exception> handler);

  /**
   * This method is used to iterate over a DomainObjectCollection and perform defined collector on each element.
   *
   * @see DomainObjectCollection#all
   */
  void accept();

  /**
   * Creates a new GradleCollectionVisitor instance with the specified collection and collector.
   *
   * @param collection The DomainObjectCollection to iterate over.
   * @param collector  The Consumer function to perform operations on each element of the collection.
   * @param <T>        The type of elements in the collection.
   * @return A new GradleCollectionVisitor instance.
   */
  static <T> GradleCollectionVisitor<T> create(@NotNull DomainObjectCollection<T> collection, @NotNull Consumer<T> collector) {
    return new GradleCollectionVisitorImpl<>(collection, collector);
  }
}
