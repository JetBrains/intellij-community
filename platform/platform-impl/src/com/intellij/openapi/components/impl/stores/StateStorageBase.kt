/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components.impl.stores

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.diagnostic.Logger
import org.jdom.Element
import java.util.concurrent.atomic.AtomicReference

public abstract class StateStorageBase<T : Any> : StateStorage {
  companion object {
    private val LOG: Logger = Logger.getInstance(javaClass<StateStorageBase<Any>>())
  }

  private var mySavingDisabled = false

  protected val storageDataRef: AtomicReference<T> = AtomicReference()

  override final fun <S> getState(component: Any?, componentName: String, stateClass: Class<S>, mergeInto: S?, reload: Boolean): S? {
    return getState(component, componentName, stateClass, true, reload, mergeInto)
  }

  fun <S : Any> getState(component: Any?, componentName: String, stateClass: Class<S>, archive: Boolean = true, reload: Boolean = false, mergeInto: S? = null): S? {
    return deserializeState(getSerializedState(getStorageData(reload), component, componentName, archive), stateClass, mergeInto)
  }

  open fun <S> deserializeState(serializedState: Element?, stateClass: Class<S>, mergeInto: S?): S? {
    return DefaultStateSerializer.deserializeState(serializedState, stateClass, mergeInto)
  }

  abstract fun getSerializedState(storageData: T, component: Any?, componentName: String, archive: Boolean = true): Element?

  protected abstract fun hasState(storageData: T, componentName: String): Boolean

  override fun hasState(componentName: String, reloadData: Boolean): Boolean {
    return hasState(getStorageData(reloadData), componentName)
  }

  public fun getStorageData(): T = getStorageData(false)

  protected fun getStorageData(reload: Boolean): T {
    val storageData = storageDataRef.get()
    if (storageData != null && !reload) {
      return storageData
    }

    val newStorageData = loadData()
    if (storageDataRef.compareAndSet(storageData, newStorageData)) {
      return newStorageData
    }
    else {
      return getStorageData(false)
    }
  }

  protected abstract fun loadData(): T

  public fun disableSaving() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Disabled saving for " + toString())
    }
    mySavingDisabled = true
  }

  public fun enableSaving() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Enabled saving " + toString())
    }
    mySavingDisabled = false
  }

  protected fun checkIsSavingDisabled(): Boolean {
    if (mySavingDisabled && LOG.isDebugEnabled()) {
      LOG.debug("Saving disabled for " + toString())
    }
    return mySavingDisabled
  }
}