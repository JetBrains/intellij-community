// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.openapi.util.Disposer;

/**
 * A marker for classes that require some work done for cleaning up.
 * To do that,
 * <ul>
 * <li>implement this interface. Please avoid using lambdas or method references because each Disposable instance needs identity to be stored in the Disposer hierarchy correctly</li>
 * <li>override the {@link #dispose()} method in your implementation and place your cleanup logic there</li>
 * <li>register the instance in {@link Disposer}</li>
 * </ul>
 * After that, when the parent {@link Disposable} object is disposed (e.g., the project is closed or a window hidden), the {@link #dispose()} method in your implementation will be called automatically by the platform.
 * <p>
 * As a general policy, you shouldn't call the {@link #dispose()} method directly,
 * Instead, register your object in the {@link Disposer} hierarchy of disposable objects via
 * {@link Disposer#register(Disposable, Disposable)} to be automatically disposed along with the the parent object.
 * </p>
 * <p>
 * If you're 100% sure that you should control your object's disposal manually,
 * do not call the {@link #dispose()} method either.
 * Use {@link Disposer#dispose(Disposable)} instead, since there might be objects registered in the chain
 * that need to be cleaned up before your object.
 * </p>
 * @see com.intellij.openapi.util.CheckedDisposable
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/disposers.html">Disposer and Disposable</a> in SDK Docs.
 * @see Disposer
 */
@SuppressWarnings("InterfaceMayBeAnnotatedFunctional") // do not use lambda as a Disposable implementation, because each Disposable instance needs identity to be stored in Disposer hierarchy correctly
public interface Disposable {
  /**
   * Usually not invoked directly, see class javadoc.
   *
   * The method shall not throw {@link com.intellij.openapi.progress.ProcessCanceledException} or {@link java.util.concurrent.CancellationException}.
   * This is considered an error.
   */
  void dispose();

  interface Default extends Disposable {

    @Override
    default void dispose() { }
  }

  interface Parent extends Disposable {
    /**
     * This method is called before {@link #dispose()}
     */
    void beforeTreeDispose();
  }
}
