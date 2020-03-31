// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

/**
 * This class marks classes, which require some work done for cleaning up.
 * As a general policy you shouldn't call the {@link #dispose} method directly,
 * but register your object to be chained with a parent disposable via {@link com.intellij.openapi.util.Disposer#register(Disposable, Disposable)}.
 * If you're 100% sure that you should control the disposal of your object manually,
 * do not call the {@link #dispose} method either. Use {@link com.intellij.openapi.util.Disposer#dispose(Disposable)} instead, since
 * there might be any object registered in chain.
 */
public interface Disposable {
  /**
   * Usually not invoked directly, see class javadoc.
   */
  void dispose();

  interface Parent extends Disposable {
    void beforeTreeDispose();
  }
}
