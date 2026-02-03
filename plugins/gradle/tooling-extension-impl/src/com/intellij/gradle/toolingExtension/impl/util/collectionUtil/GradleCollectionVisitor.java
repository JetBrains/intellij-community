// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.collectionUtil;

import org.gradle.api.DomainObjectCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

@ApiStatus.Experimental
public interface GradleCollectionVisitor<T> {

  /**
   * Defines the element collector which will be called on each element in DomainObjectCollection.
   */
  void visit(T element);

  /**
   * Defines the failure handler for the Gradle collection visitor.
   * This handler is invoked when an exception occurs during the collection process.
   */
  void onFailure(T element, @NotNull Exception exception);

  /**
   * Defines the handler of an element that was added into a Gradle collection after iteration over this collection.
   * This handler is invoked when an element registers in a collection when the collecting process is finished.
   */
  void visitAfterAccept(T element);

  /**
   * This method is used to iterate over a DomainObjectCollection and perform defined collector on each element.
   *
   * @param collection The DomainObjectCollection to iterate over.
   * @param visitor  The Consumer function to perform operations on each element of the collection.
   * @param <T>        The type of elements in the collection.
   */
  static <T> void accept(@NotNull DomainObjectCollection<T> collection, @NotNull GradleCollectionVisitor<? super T> visitor) {
    AtomicBoolean isCollected = new AtomicBoolean(false);
    collection.all(element -> {
      if (isCollected.get()) {
        visitor.visitAfterAccept(element);
      }
      try {
        visitor.visit(element);
      }
      catch (Exception exception) {
        visitor.onFailure(element, exception);
      }
    });
    isCollected.set(true);
  }
}
