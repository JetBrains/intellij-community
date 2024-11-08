// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "DEPRECATION")

package com.intellij.openapi.actionSystem.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.ide.impl.DataValidators
import com.intellij.ide.impl.GetDataRuleType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.AnActionEvent.InjectedDataContextSupplier
import com.intellij.openapi.actionSystem.impl.Utils.isModalContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.Strings
import com.intellij.reference.SoftReference
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.*
import com.intellij.util.keyFMap.KeyFMap
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

private val LOG = Logger.getInstance(PreCachedDataContext::class.java)

private var ourPrevMapEventCount = 0
private val ourPrevMaps = ContainerUtil.createWeakKeySoftValueMap<Component, FList<ProviderData>>()
private val ourComponents = ContainerUtil.createWeakSet<Component>()
private val ourInstances = UnsafeWeakList<PreCachedDataContext>()
private val ourDataKeysIndices = ConcurrentHashMap<String, Int>()
private val ourDataKeysCount = AtomicInteger()
private val ourEDTWarnsInterner = Interner.createStringInterner()
private var ourIsCapturingSnapshot = false

internal open class PreCachedDataContext : AsyncDataContext, UserDataHolder, InjectedDataContextSupplier {
  private val myComponentRef: ComponentRef
  private val myUserData: AtomicReference<KeyFMap>
  private val myCachedData: FList<ProviderData>
  private val myDataManager: DataManagerImpl
  private val myDataKeysCount: Int

  constructor(component: Component?) {
    myComponentRef = ComponentRef(component)
    myUserData = AtomicReference(KeyFMap.EMPTY_MAP)
    myDataManager = DataManager.getInstance() as DataManagerImpl
    if (component == null) {
      myCachedData = FList.emptyList()
      myDataKeysCount = DataKey.allKeysCount()
      return
    }

    ThreadingAssertions.assertEventDispatchThread()
    ourIsCapturingSnapshot = true
    try {
      ProhibitAWTEvents.start("getData").use { ignore1 ->
        SlowOperations.startSection(SlowOperations.FORCE_ASSERT).use { ignore2 ->
          val count = ActivityTracker.getInstance().count
          if (ourPrevMapEventCount != count ||
              ourDataKeysIndices.size != DataKey.allKeysCount() ||
              ApplicationManager.getApplication().isUnitTestMode()) {
            ourPrevMaps.clear()
            ourComponents.clear()
          }
          val components = generateSequence(component) { UIUtil.getParent(it) }
            .takeWhile { ourPrevMaps[it] == null || it === component && !ourComponents.contains(it) }
            .toList().asReversed()
          val topParent = if (components.isEmpty()) component else UIUtil.getParent(components[0])
          val initial = if (topParent == null) FList.emptyList() else ourPrevMaps[topParent]!!

          var keyCount: Int
          var cachedData: FList<ProviderData>
          val sink = MySink()
          while (true) {
            sink.keys = null
            cachedData = cacheComponentsData(sink, components, initial, myDataManager)
            cachedData = runSnapshotRules(sink, component, cachedData)
            keyCount = sink.keys?.size ?: DataKey.allKeysCount()
            // retry if providers add new keys
            if (keyCount == DataKey.allKeysCount()) break
          }
          myDataKeysCount = keyCount
          myCachedData = cachedData
          ourInstances.add(this)
          ourComponents.add(component)
          ourPrevMapEventCount = count
        }
      }
    }
    finally {
      ourIsCapturingSnapshot = false
    }
  }

  private constructor(
    compRef: ComponentRef,
    cachedData: FList<ProviderData>,
    userData: AtomicReference<KeyFMap>,
    dataManager: DataManagerImpl,
    dataKeysCount: Int
  ) {
    myComponentRef = compRef
    myCachedData = cachedData
    myUserData = userData
    myDataManager = dataManager
    myDataKeysCount = dataKeysCount
  }

  fun cachesAllKnownDataKeys(): Boolean {
    return myDataKeysCount == DataKey.allKeysCount()
  }

  override fun getInjectedDataContext(): DataContext {
    return this as? InjectedDataContext
           ?: InjectedDataContext(myComponentRef, myCachedData, myUserData, myDataManager, myDataKeysCount)
  }

  fun prependProvider(dataProvider: Any?): PreCachedDataContext {
    if (dataProvider == null) return this
    val isEDT = EDT.isCurrentThreadEdt()
    var keyCount: Int
    var cachedData: FList<ProviderData>
    val sink = MySink()
    while (true) {
      val component = SoftReference.dereference(myComponentRef.ref)
      sink.keys = null
      sink.hideEditor = hideEditor(component)
      cacheProviderData(sink, dataProvider, myDataManager)
      cachedData = if (sink.map == null) myCachedData else myCachedData.prepend(sink.map)
      // do not provide CONTEXT_COMPONENT in BGT
      cachedData = runSnapshotRules(sink, if (isEDT) component else null, cachedData)
      keyCount = sink.keys?.size ?: DataKey.allKeysCount()
      // retry if the provider adds new keys
      if (keyCount == DataKey.allKeysCount()) break
    }
    val userData = AtomicReference(KeyFMap.EMPTY_MAP)
    return when (this) {
      is InjectedDataContext -> InjectedDataContext(myComponentRef, cachedData, userData, myDataManager, keyCount)
      else -> PreCachedDataContext(myComponentRef, cachedData, userData, myDataManager, keyCount)
    }
  }

  @Suppress("DuplicatedCode")
  @Deprecated("Deprecated in Java")
  override fun getData(dataId: String): Any? {
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId)) return SoftReference.dereference(myComponentRef.ref)
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.`is`(dataId)) return myComponentRef.modalContext
    if (PlatformDataKeys.MODALITY_STATE.`is`(dataId)) return myComponentRef.modalityState
    if (PlatformDataKeys.SPEED_SEARCH_TEXT.`is`(dataId) && myComponentRef.speedSearchText != null) return myComponentRef.speedSearchText
    if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.`is`(dataId) && myComponentRef.speedSearchRef != null) return myComponentRef.speedSearchRef.get()
    if (myCachedData.isEmpty()) return null

    val isEDT = EDT.isCurrentThreadEdt()
    if (isEDT && ourIsCapturingSnapshot) {
      reportGetDataInsideCapturingSnapshot()
    }
    val noRulesSection = isEDT && ActionUpdater.isNoRulesInEDTSection
    val rulesSuppressed = isEDT && LoadingState.COMPONENTS_LOADED.isOccurred &&
                          Registry.`is`("actionSystem.update.actions.suppress.dataRules.on.edt")
    val rulesAllowed = !CommonDataKeys.PROJECT.`is`(dataId) && !rulesSuppressed && !noRulesSection
    var answer = getDataInner(dataId, rulesAllowed, !noRulesSection)

    val map = myCachedData[0]
    if (answer == null && rulesAllowed) {
      val keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1)
      if (!map.nullsByContextRules.get(keyIndex)) {
        answer = myDataManager.getDataFromRules(dataId, GetDataRuleType.CONTEXT, ContextRuleProvider())
        if (answer != null) {
          map.computedData.put(dataId, answer)
          map.nullsByRules.clear(keyIndex)
        }
        else {
          map.nullsByContextRules.set(keyIndex)
        }
      }
    }
    if (answer == null && rulesSuppressed) {
      val throwable = Throwable()
      AppExecutorUtil.getAppExecutorService().execute {
        if (ReadAction.compute(ThrowableComputable { getData(dataId) }) != null) {
          LOG.warn("$dataId is not available on EDT. Code that depends on data rules and slow data providers " +
                   "must be run in background. For example, an action must use `ActionUpdateThread.BGT`.", throwable)
        }
      }
    }
    answer = wrapUnsafeData(answer)
    return if (answer === CustomizedDataContext.EXPLICIT_NULL) null else answer
  }

  protected open fun getDataInner(dataId: String, rulesAllowed: Boolean, ruleValuesAllowed: Boolean): Any? {
    val keyIndex: Int = getDataKeyIndex(dataId)
    if (keyIndex == -1) return CustomizedDataContext.EXPLICIT_NULL // DataKey not found

    var answer: Any? = null
    for (map in myCachedData) {
      ProgressManager.checkCanceled()
      var isComputed = false
      answer = map.uiSnapshot[dataId]
      if (answer == null) {
        answer = map.computedData[dataId]
        isComputed = true
      }
      if (answer === CustomizedDataContext.EXPLICIT_NULL) break
      if (answer != null) {
        if (isComputed) {
          reportValueProvidedByRulesUsage(dataId, !ruleValuesAllowed)
          if (!ruleValuesAllowed) return null
        }
        answer = DataValidators.validOrNull(answer, dataId, this)
        if (answer != null) break
        if (!isComputed) return null
        // allow slow data providers and rules to re-calc the value
        map.computedData.remove(dataId)
      }
      if (!rulesAllowed || map.nullsByRules.get(keyIndex)) continue

      answer = myDataManager.getDataFromRules(dataId, GetDataRuleType.PROVIDER, map)

      if (answer == null) {
        map.nullsByRules.set(keyIndex)
      }
      else {
        map.computedData.put(dataId, answer)
        break
      }
    }
    return answer
  }

  private fun reportGetDataInsideCapturingSnapshot() {
    LOG.error("DataContext must not be queried during another DataContext creation")
  }

  private fun reportValueProvidedByRulesUsage(dataId: String, error: Boolean) {
    if (!Registry.`is`("actionSystem.update.actions.warn.dataRules.on.edt")) return
    if (EDT.isCurrentThreadEdt() &&
        SlowOperations.isInSection(SlowOperations.ACTION_UPDATE) &&
        ActionUpdater.currentInEDTOperationName() != null &&
        !SlowOperations.isAlwaysAllowed()) {
      val message = "'$dataId' is requested on EDT by ${ActionUpdater.currentInEDTOperationName()}. See ActionUpdateThread javadoc."
      if (!Strings.areSameInstance(message, ourEDTWarnsInterner.intern(message))) return
      val th = if (error) Throwable(message) else null
      AppExecutorUtil.getAppExecutorService().execute {
        if (error) {
          LOG.error(th as Throwable)
        }
        else {
          LOG.warn(message)
        }
      }
    }
  }

  @Suppress("DuplicatedCode")
  fun getRawDataIfCached(dataId: String, uiOnly: Boolean): Any? {
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId)) return SoftReference.dereference(myComponentRef.ref)
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.`is`(dataId)) return myComponentRef.modalContext
    if (PlatformDataKeys.MODALITY_STATE.`is`(dataId)) return myComponentRef.modalityState
    if (PlatformDataKeys.SPEED_SEARCH_TEXT.`is`(dataId) && myComponentRef.speedSearchText != null) return myComponentRef.speedSearchText
    if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.`is`(dataId) && myComponentRef.speedSearchRef != null) return myComponentRef.speedSearchRef.get()
    if (myCachedData.isEmpty()) return null

    for (map in myCachedData) {
      var answer = map.uiSnapshot[dataId]
      if (answer == null && !uiOnly) answer = map.computedData[dataId]
      if (answer != null) {
        return if (answer === CustomizedDataContext.EXPLICIT_NULL) null else answer
      }
    }
    return null
  }

  companion object {
    @JvmStatic
    fun clearAllCaches() {
      for (list in ourPrevMaps.values) {
        for (map in list) {
          map.uiSnapshot.clear()
          map.computedData.clear()
        }
      }
      ourPrevMaps.clear()
      ourComponents.clear()
      for (context in ourInstances) {
        for (map in context.myCachedData) {
          map.uiSnapshot.clear()
          map.computedData.clear()
        }
      }
      ourInstances.clear()
    }

    @JvmStatic
    fun customize(context: AsyncDataContext, provider: Any?): AsyncDataContext {
      if (provider == null) return context
      val sink = MySink()
      cacheProviderData(sink, provider, DataManager.getInstance() as DataManagerImpl)
      val snapshot = sink.map?.uiSnapshot
      if (snapshot == null) return context
      return AsyncDataContext { dataId: String ->
        DataManager.getInstance().getCustomizedData(dataId, context, DataProvider { snapshot[it] })
      }
    }
  }

  override fun <T> getUserData(key: Key<T>): T? {
    return myUserData.get().get(key)
  }

  override fun <T> putUserData(key: Key<T>, value: T?) {
    while (true) {
      val map = myUserData.get()
      val newMap = if (value == null) map.minus(key) else map.plus(key, value)
      if (newMap === map || myUserData.compareAndSet(map, newMap)) {
        break
      }
    }
  }

  override fun toString(): String {
    return (if (this is InjectedDataContext) "injected:" else "") +
           "component=${getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)}"
  }

  private inner class ContextRuleProvider : DataProvider, DataValidators.SourceWrapper {
    override fun getData(dataId: String): Any? {
      return getDataInner(dataId, !CommonDataKeys.PROJECT.`is`(dataId), true)
    }

    override fun unwrapSource(): Any {
      return this@PreCachedDataContext
    }
  }

  private class InjectedDataContext(
    compRef: ComponentRef,
    cachedData: FList<ProviderData>,
    userData: AtomicReference<KeyFMap>,
    dataManager: DataManagerImpl,
    dataKeysCount: Int
  ) : PreCachedDataContext(compRef, cachedData, userData, dataManager, dataKeysCount) {
    override fun getDataInner(dataId: String, rulesAllowedBase: Boolean, ruleValuesAllowed: Boolean): Any? {
      return InjectedDataKeys.getInjectedData(dataId, DataProvider { dataId ->
        super.getDataInner(dataId, rulesAllowedBase, ruleValuesAllowed)
      })
    }
  }
}

private fun getDataKeyIndex(dataId: String): Int {
  var keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1)
  if (keyIndex == -1 && ourDataKeysIndices.size < DataKey.allKeysCount()) {
    for (key in DataKey.allKeys()) {
      ourDataKeysIndices.computeIfAbsent(key.name) { ourDataKeysCount.getAndIncrement() }
    }
    keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1)
  }
  return keyIndex
}

private fun cacheComponentsData(
  sink: MySink,
  components: List<Component>,
  initial: FList<ProviderData>,
  dataManager: DataManagerImpl
): FList<ProviderData> {
  var cachedData = initial
  if (components.isEmpty()) return cachedData
  val start = System.currentTimeMillis()
  for (comp in components) {
    sink.map = null
    sink.hideEditor = hideEditor(comp)
    val dataProvider = if (comp is UiDataProvider) comp else DataManagerImpl.getDataProviderEx(comp)
    cacheProviderData(sink, dataProvider, dataManager)
    cachedData = if (sink.map == null) cachedData else cachedData.prepend(sink.map)
    ourPrevMaps.put(comp, cachedData)
  }
  val time = System.currentTimeMillis() - start
  @Suppress("ControlFlowWithEmptyBody")
  if (time > 200) {
    // nothing
  }
  return cachedData
}

private fun cacheProviderData(
  sink: MySink,
  dataProvider: Any?,
  dataManager: DataManagerImpl
) {
  DataSink.uiDataSnapshot(sink, dataProvider)
  sink.map?.let { map -> // no data - no rules
    for (key in dataManager.keysForRuleType(GetDataRuleType.FAST)) {
      val data = dataManager.getDataFromRules(
        key.name, GetDataRuleType.FAST, DataProvider { map.uiSnapshot[it] })
      if (data == null) continue
      map.uiSnapshot.putIfAbsent(key.name, data)
    }
  }
  if (sink.hideEditor) {
    val map = sink.map ?: ProviderData().also { sink.map = it }
    map.uiSnapshot.put(CommonDataKeys.EDITOR.name, CustomizedDataContext.EXPLICIT_NULL)
    map.uiSnapshot.put(CommonDataKeys.HOST_EDITOR.name, CustomizedDataContext.EXPLICIT_NULL)
    map.uiSnapshot.put(InjectedDataKeys.EDITOR.name, CustomizedDataContext.EXPLICIT_NULL)
  }
}

private fun runSnapshotRules(
  sink: MySink,
  component: Component?,
  cachedData: FList<ProviderData>
): FList<ProviderData> {
  val noMap = sink.map == null
  val snapshot: DataSnapshot = object : DataSnapshot {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(key: DataKey<T>): T? {
      if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT) return component as T?
      for (map in cachedData) {
        val answer = map.uiSnapshot[key.name]
        when {
          answer === CustomizedDataContext.EXPLICIT_NULL -> return null
          answer != null -> return answer as T
        }
      }
      return null
    }
  }
  UiDataRule.forEachRule { rule ->
    val prev = sink.source
    sink.source = rule
    sink.cachedDataForRules = cachedData
    try {
      rule.uiDataSnapshot(sink, snapshot)
    }
    finally {
      sink.cachedDataForRules = null
      sink.source = prev
    }
  }
  return if (noMap && sink.map != null) cachedData.prepend(sink.map) else cachedData
}

private class MySink : DataSink {
  var map: ProviderData? = null
  var source: Any? = null
  var keys: Array<DataKey<*>>? = null

  var hideEditor: Boolean = false
  var cachedDataForRules: FList<ProviderData>? = null

  override fun <T : Any> set(key: DataKey<T>, data: T?) {
    if (data == null) return
    if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
        key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
        key == PlatformDataKeys.MODALITY_STATE) {
      return
    }
    val validated = when {
      data === CustomizedDataContext.EXPLICIT_NULL -> data
      key == PlatformCoreDataKeys.BGT_DATA_PROVIDER -> {
        val existing = map?.uiSnapshot[key.name] as DataProvider?
        when {
          existing == null -> data
          cachedDataForRules == null -> CompositeDataProvider.compose(data as DataProvider, existing)
          else -> CompositeDataProvider.compose(existing, data as DataProvider)
        }
      }
      else -> DataValidators.validOrNull(data, key.name, source!!)
    }
    if (validated == null) return
    val map = map ?: ProviderData().also { map = it }
    if (cachedDataForRules != null && key != PlatformCoreDataKeys.BGT_DATA_PROVIDER) {
      if (map.uiSnapshot[key.name] != null) {
        return
      }
      for (map in cachedDataForRules) {
        if (map.uiSnapshot[key.name] != null) {
          return
        }
      }
    }
    map.uiSnapshot.put(key.name, validated)
    if (key == CommonDataKeys.EDITOR && validated !== CustomizedDataContext.EXPLICIT_NULL) {
      map.uiSnapshot.put(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.name, validated)
    }
  }

  override fun <T : Any> setNull(key: DataKey<T>) {
    if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT || key == PlatformCoreDataKeys.IS_MODAL_CONTEXT || key == PlatformDataKeys.MODALITY_STATE) {
      return
    }
    val map = map ?: ProviderData().also { map = it }
    map.uiSnapshot.put(key.name, CustomizedDataContext.EXPLICIT_NULL)
  }

  override fun <T : Any> lazy(key: DataKey<T>, data: () -> T?) {
    set(PlatformCoreDataKeys.BGT_DATA_PROVIDER, MyLazy(key, data))
  }

  override fun <T : Any> lazyNull(key: DataKey<T>) {
    set(PlatformCoreDataKeys.BGT_DATA_PROVIDER, DataProvider { dataId ->
      if (key.`is`(dataId)) CustomizedDataContext.EXPLICIT_NULL else null
    })
  }

  override fun uiDataSnapshot(provider: UiDataProvider) {
    val prev = source
    source = provider
    try {
      provider.uiDataSnapshot(this)
    }
    finally {
      source = prev
    }
  }

  override fun dataSnapshot(provider: DataSnapshotProvider) {
    val prev = source
    source = provider
    try {
      provider.dataSnapshot(this)
    }
    finally {
      source = prev
    }
  }

  override fun uiDataSnapshot(provider: DataProvider) {
    val prev = source
    source = provider
    try {
      val keys = keys ?: DataKey.allKeys().also { keys = it }
      for (key in keys) {
        val data: Any? = key.getData(provider)
        if (data != null) {
          @Suppress("UNCHECKED_CAST")
          set(key as DataKey<Any>, data)
        }
      }
    }
    finally {
      source = prev
    }
  }
}

private class MyLazy<T>(val key: DataKey<T>, val supplier: () -> T?) : DataProvider, DataValidators.SourceWrapper {
  override fun getData(dataId: String): Any? {
    return if (key.`is`(dataId)) supplier.invoke() else null
  }

  override fun unwrapSource(): Any {
    return supplier
  }
}

private fun hideEditor(component: Component?): Boolean {
  return component is JComponent &&
         component.getClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY) != null
}

private class ProviderData : DataProvider, DataValidators.SourceWrapper {
  val uiSnapshot = ConcurrentHashMap<String, Any>()
  val computedData = ConcurrentHashMap<String, Any>()

  // to avoid lots of nulls in maps
  val nullsByRules = ConcurrentBitSet.create()
  val nullsByContextRules = ConcurrentBitSet.create()

  override fun getData(dataId: String): Any? {
    return uiSnapshot[dataId] ?: computedData[dataId]
  }

  override fun unwrapSource(): Any {
    return emptyMap<Any, Any>()
  }
}

private class ComponentRef(component: Component?) {
  val ref = component?.let { WeakReference(it) }
  val modalityState = component?.let { ModalityState.stateForComponent(it) } ?: ModalityState.nonModal()
  val modalContext = component?.let { isModalContext(it) }

  val speedSearchText: String?
  val speedSearchRef: Reference<Component>?

  init {
    val supply = (component as? JComponent)?.let { SpeedSearchSupply.getSupply(it) }
    speedSearchText = supply?.enteredPrefix
    speedSearchRef = (supply as? SpeedSearchBase<*>)?.getSearchField()?.let { WeakReference(it) }
  }
}