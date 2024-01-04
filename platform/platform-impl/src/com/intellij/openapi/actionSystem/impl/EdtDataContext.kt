// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue.Companion.getInstance
import com.intellij.ide.IdeView
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.ide.impl.GetDataRuleType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.AnActionEvent.InjectedDataContextSupplier
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolder
import com.intellij.reference.SoftReference
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.keyFMap.KeyFMap
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import javax.swing.JComponent

/**
 * This is an internal API. Do not use it.
 *
 * Do not create directly, use [DataManager.getDataContext] instead.
 * Do not cast to [UserDataHolder], use [DataManager.saveInDataContext] and [DataManager.loadFromDataContext] instead.
 * Do not override.
 */
@ApiStatus.Internal
@ApiStatus.NonExtendable
open class EdtDataContext : DataContext, UserDataHolder, InjectedDataContextSupplier {
  private var eventCount: Int

  // To prevent memory leak, we have to wrap passed component into the weak reference.
  // For example, Swing often remembers menu items that have DataContext as a field.
  private val ref: Reference<Component>?
  private val userData: Ref<KeyFMap>
  private val dataManager: DataManagerImpl
  private val cachedData: MutableMap<String, Any>

  constructor(component: Component?) {
    eventCount = -1
    ref = if (component == null) null else WeakReference(component)
    cachedData = ContainerUtil.createWeakValueMap()
    userData = Ref(KeyFMap.EMPTY_MAP)
    dataManager = DataManager.getInstance() as DataManagerImpl
  }

  private constructor(componentReference: Reference<Component>?,
                      cachedData: MutableMap<String, Any>,
                      userData: Ref<KeyFMap>,
                      dataManager: DataManagerImpl,
                      eventCount: Int) {
    ref = componentReference
    this.cachedData = cachedData
    this.userData = userData
    this.dataManager = dataManager
    this.eventCount = eventCount
  }

  override fun getInjectedDataContext(): DataContext {
    return this as? InjectedDataContext ?: InjectedDataContext(componentReference = ref,
                                                               cachedData = cachedData,
                                                               userData = userData,
                                                               manager = dataManager,
                                                               eventCount = eventCount)
  }

  internal fun setEventCount(eventCount: Int) {
    cachedData.clear()
    this.eventCount = eventCount
  }

  override fun getData(dataId: String): Any? {
    ProgressManager.checkCanceled()
    val cacheable: Boolean
    val currentEventCount = if (EDT.isCurrentThreadEdt()) getInstance().eventCount else -1
    if (eventCount == -1 || eventCount == currentEventCount) {
      cacheable = true
    }
    else {
      cacheable = false
      logger<EdtDataContext>().error("cannot share data context between Swing events;" +
                                     " initial event count = $eventCount; current event count = $currentEventCount")
    }

    var answer = getDataInner(dataId, cacheable)
    if (answer == null) {
      answer = dataManager.getDataFromRules(dataId, GetDataRuleType.CONTEXT) { getDataInner(dataId = it, cacheable = cacheable) }
      if (cacheable) {
        cachedData.put(dataId, answer ?: CustomizedDataContext.EXPLICIT_NULL)
      }
    }
    answer = wrapUnsafeData(answer)
    return if (answer === CustomizedDataContext.EXPLICIT_NULL) null else answer
  }

  protected open fun getDataInner(dataId: String, cacheable: Boolean): Any? {
    val component = SoftReference.dereference(ref)
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.`is`(dataId)) {
      return if (component == null) null else Utils.isModalContext(component)
    }

    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId)) {
      return component
    }

    if (PlatformDataKeys.MODALITY_STATE.`is`(dataId)) {
      return if (component == null) ModalityState.nonModal() else ModalityState.stateForComponent(component)
    }

    if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.`is`(dataId) || PlatformDataKeys.SPEED_SEARCH_TEXT.`is`(dataId)) {
      val supply = if (component is JComponent) SpeedSearchSupply.getSupply(component) else null
      val result: Any? = when {
        supply == null -> null
        PlatformDataKeys.SPEED_SEARCH_TEXT.`is`(dataId) -> supply.enteredPrefix
        supply is SpeedSearchBase<*> -> supply.searchField
        else -> null
      }
      if (result != null) {
        return result
      }
    }

    var answer = if (cacheable) cachedData.get(dataId) else null
    if (answer != null) {
      return answer
    }

    answer = calcData(dataId, component)
    if (CommonDataKeys.EDITOR.`is`(dataId) || CommonDataKeys.HOST_EDITOR.`is`(dataId) || InjectedDataKeys.EDITOR.`is`(dataId)) {
      answer = DataManagerImpl.validateEditor(answer as Editor?, component)
    }
    if (cacheable) {
      cachedData.put(dataId, answer ?: CustomizedDataContext.EXPLICIT_NULL)
    }
    return answer
  }

  private fun calcData(dataId: String, component: Component?): Any? {
    ProhibitAWTEvents.start("getData").use {
      var c = component
      while (c != null) {
        val dataProvider = DataManagerImpl.getDataProviderEx(c)
        if (dataProvider == null) {
          c = UIUtil.getParent(c)
          continue
        }

        val data = dataManager.getDataFromProviderAndRules(dataId, GetDataRuleType.PROVIDER, dataProvider)
        if (data != null) {
          return data
        }
        c = UIUtil.getParent(c)
      }
    }
    return null
  }

  internal fun getRawDataIfCached(dataId: String): Any? {
    val data = cachedData.get(dataId)
    return if (data === CustomizedDataContext.EXPLICIT_NULL) null else data
  }

  override fun <T> getUserData(key: Key<T>): T? = userData.get().get(key)

  override fun <T> putUserData(key: Key<T>, value: T?) {
    val map = userData.get()
    userData.set(if (value == null) map.minus(key) else map.plus(key, value))
  }

  override fun toString(): @NonNls String {
    return "${if (this is InjectedDataContext) "injected:" else ""}component=${SoftReference.dereference(ref)}"
  }

  private class InjectedDataContext(componentReference: Reference<Component>?,
                                    cachedData: MutableMap<String, Any>,
                                    userData: Ref<KeyFMap>,
                                    manager: DataManagerImpl,
                                    eventCount: Int)
    : EdtDataContext(componentReference, cachedData, userData, manager, eventCount) {
    override fun getDataInner(dataId: String, cacheable: Boolean): Any? {
      return InjectedDataKeys.getInjectedData(dataId) { key -> super.getDataInner(key, cacheable) }
    }
  }
}

internal fun wrapUnsafeData(data: Any?): Any? = when {
  data is IdeView -> safeIdeView(data)
  else -> data
}
