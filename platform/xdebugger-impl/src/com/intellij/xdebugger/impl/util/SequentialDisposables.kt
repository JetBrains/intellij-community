package com.intellij.xdebugger.impl.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

internal class SequentialDisposables(parent: Disposable? = null) : Disposable {
  private var myCurrentDisposable: Disposable? = null

  init {
    parent?.onTermination(this)
  }

  fun next(): Disposable {
    val newDisposable = Disposer.newDisposable()
    Disposer.register(this, newDisposable)

    myCurrentDisposable?.disposeIfNeeded()
    myCurrentDisposable = newDisposable

    return newDisposable
  }

  fun terminateCurrent() {
    myCurrentDisposable?.disposeIfNeeded()
    myCurrentDisposable = null
  }

  private fun Disposable.disposeIfNeeded() {
    if (this.isAlive)
      Disposer.dispose(this)
  }

  override fun dispose() = terminateCurrent()
}