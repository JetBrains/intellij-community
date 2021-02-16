// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

abstract class GHSimpleLoadingModel<T> : GHEventDispatcherLoadingModel() {
  override var loading: Boolean = false
    protected set
  override var resultAvailable: Boolean = false
    protected set
  override var error: Throwable? = null
    protected set
  var result: T? = null
    protected set
}