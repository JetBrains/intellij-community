// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.TestOnly
import java.lang.System.identityHashCode

@TestOnly
inline fun <reified T : PersistentStateComponent<R>, reified R : BaseState> T.reloadApplicationState(
  newState: () -> R = { R::class.java.getConstructor().newInstance() },
) {
  val stateAnnotation = T::class.java.getAnnotation(State::class.java)

  requireNotNull(stateAnnotation) {
    "@" + State::class.simpleName + " annotation is missing, but required for the " + T::class.java.simpleName
  }

  require(stateAnnotation.allowLoadInTests) {
    State::class.simpleName + "::" + State::allowLoadInTests.name + " must be true for the " + T::class.java.simpleName
  }

  //just to check if the state can be saved
  val store = ApplicationManager.getApplication().stateStore
  val p = service<T>()
  require(this == p) {
    "Application service must be used, but was $this (${identityHashCode(this)}) vs $p (${identityHashCode(p)})"
  }

  store.saveComponent(p)

  //wipe the state in the component
  p.loadState(newState())

  //ask the storage subsystem to load it the state once again
  store.reloadState(T::class.java)
}
