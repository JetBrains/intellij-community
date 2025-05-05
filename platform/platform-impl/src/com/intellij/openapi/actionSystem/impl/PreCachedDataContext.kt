// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "DEPRECATION")

package com.intellij.openapi.actionSystem.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.IdeView
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.ide.impl.DataManagerImpl.KeyedDataProvider
import com.intellij.ide.impl.DataManagerImpl.ParametrizedDataProvider
import com.intellij.ide.impl.DataValidators
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.AnActionEvent.InjectedDataContextSupplier
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.BGT_DATA_PROVIDER
import com.intellij.openapi.actionSystem.impl.Utils.isModalContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorComponentImpl
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
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Interner
import com.intellij.util.containers.UnsafeWeakList
import com.intellij.util.keyFMap.KeyFMap
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentMapOf
import java.awt.Component
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.text.JTextComponent

private val LOG = Logger.getInstance(PreCachedDataContext::class.java)

private var ourPrevMapEventCount = 0
private val ourPrevMaps = ContainerUtil.createWeakKeySoftValueMap<Component, CachedData>()
private val ourInstances = UnsafeWeakList<PreCachedDataContext>()
private val ourDataKeysIndices = ConcurrentHashMap<String, Int>()
private val ourDataKeysCount = AtomicInteger()
private val ourEDTWarnsInterner = Interner.createStringInterner()
private var ourIsCapturingSnapshot = false

internal open class PreCachedDataContext : AsyncDataContext, UserDataHolder, InjectedDataContextSupplier {
  private val myComponentRef: ComponentRef
  private val myUserData: AtomicReference<KeyFMap>
  private val myCachedData: CachedData
  private val myDataManager: DataManagerImpl
  private val myDataKeysCount: Int

  constructor() {
    myComponentRef = ComponentRef(null)
    myUserData = AtomicReference(KeyFMap.EMPTY_MAP)
    myDataManager = DataManager.getInstance() as DataManagerImpl
    myCachedData = CachedData(false, persistentHashMapOf())
    myDataKeysCount = DataKey.allKeysCount()
  }

  constructor(component: Component, forceUseCachesInTests: Boolean) {
    myComponentRef = ComponentRef(component)
    myUserData = AtomicReference(KeyFMap.EMPTY_MAP)
    myDataManager = DataManager.getInstance() as DataManagerImpl

    ThreadingAssertions.assertEventDispatchThread()
    ourIsCapturingSnapshot = true
    try {
      ProhibitAWTEvents.start("getData").use { _ ->
        SlowOperations.startSection(SlowOperations.FORCE_ASSERT).use { _ ->
          val count = ActivityTracker.getInstance().count
          if (forceUseCachesInTests) {
            assert(ourPrevMapEventCount == count) { "Previous event count $ourPrevMapEventCount != $count" }
          }
          else if (ourPrevMapEventCount != count ||
                   ApplicationManager.getApplication().isUnitTestMode()) {
            ourPrevMaps.clear()
          }
          val isDisplayable = component.isDisplayable
          val components = generateSequence(component) { UIUtil.getParent(it) }
            .takeWhile { ourPrevMaps[it]?.isDisplayable != isDisplayable }
            .toList().asReversed()
          val topParent = if (components.isEmpty()) component else UIUtil.getParent(components[0])
          val initial = if (topParent == null) null else ourPrevMaps[topParent]!!

          var keyCount: Int
          var cachedData: CachedData?
          val sink = MySink()
          while (true) {
            sink.keys = null
            sink.uiSnapshot = initial?.uiSnapshot?.builder()
            cachedData = cacheComponentsData(sink, isDisplayable, components)
            if (initial?.uiComputed?.isNotEmpty() == true) {
              cachedData.uiComputed.putAll(initial.uiComputed)
            }
            else {
              runSnapshotRules(sink, component, cachedData)
            }
            keyCount = sink.keys?.size ?: DataKey.allKeysCount()
            // retry if providers add new keys
            // drop together with MySink.uiDataSnapshot(DataProvider)
            if (keyCount == DataKey.allKeysCount()) break
          }
          myDataKeysCount = keyCount
          myCachedData = cachedData
          ourInstances.add(this)
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
    cachedData: CachedData,
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
    var cachedData: CachedData
    val sink = MySink()
    while (true) {
      val component = SoftReference.dereference(myComponentRef.ref)
      sink.keys = null
      sink.uiSnapshot = myCachedData.uiSnapshot.builder()
      cacheProviderData(sink, dataProvider)
      cachedData = CachedData(myCachedData.isDisplayable, sink.uiSnapshot?.build() ?: persistentMapOf())
      // do not provide CONTEXT_COMPONENT in BGT
      runSnapshotRules(sink, if (isEDT) component else null, cachedData)
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

    val isEDT = EDT.isCurrentThreadEdt()
    if (isEDT && ourIsCapturingSnapshot) {
      reportGetDataInsideCapturingSnapshot()
    }
    val noRulesSection = isEDT && ActionUpdater.isNoRulesInEDTSection
    val rulesSuppressed = isEDT && LoadingState.COMPONENTS_LOADED.isOccurred &&
                          Registry.`is`("actionSystem.update.actions.suppress.dataRules.on.edt")
    val rulesAllowed = !CommonDataKeys.PROJECT.`is`(dataId) && !rulesSuppressed && !noRulesSection

    val answer = getDataInner(dataId, rulesAllowed, !noRulesSection)
    if (answer == null && rulesSuppressed) {
      val throwable = Throwable()
      AppExecutorUtil.getAppExecutorService().execute {
        if (ReadAction.compute(ThrowableComputable { getData(dataId) }) != null) {
          LOG.warn("$dataId is not available on EDT. Code that depends on data rules and slow data providers " +
                   "must be run in background. For example, an action must use `ActionUpdateThread.BGT`.", throwable)
        }
      }
    }
    return when {
      answer === CustomizedDataContext.EXPLICIT_NULL -> null
      else -> wrapUnsafeData(answer)
    }
  }

  protected open fun getDataInner(dataId: String, rulesAllowed: Boolean, ruleValuesAllowed: Boolean): Any? {
    val keyIndex: Int = getDataKeyIndex(dataId)
    if (keyIndex == -1) return CustomizedDataContext.EXPLICIT_NULL // DataKey not found

    var answer: Any?
    ProgressManager.checkCanceled()
    var isComputed = false
    answer = myCachedData.uiData(dataId) ?: myCachedData.bgtComputed[dataId]?.also {
      isComputed = true
    }
    if (answer === CustomizedDataContext.EXPLICIT_NULL) {
      return answer
    }
    else if (answer != null) {
      if (isComputed) {
        reportValueProvidedByRulesUsage(dataId, !ruleValuesAllowed)
        if (!ruleValuesAllowed) return null
      }
      answer = DataValidators.validOrNull(answer, dataId, this)
      if (answer != null) return answer
      if (!isComputed) return null
      // allow slow data providers and rules to re-calc the value
      myCachedData.bgtComputed.remove(dataId)
    }
    if (!rulesAllowed || myCachedData.nullsByRules.get(keyIndex)) return null

    answer = myDataManager.getDataFromRules(dataId, ContextRuleProvider())

    if (answer == null) {
      myCachedData.nullsByRules.set(keyIndex)
    }
    else {
      myCachedData.bgtComputed[dataId] = answer
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

    val answer = myCachedData.uiData(dataId) ?: run {
      if (!uiOnly) myCachedData.bgtComputed[dataId] else null
    }
    return when {
      answer === CustomizedDataContext.EXPLICIT_NULL -> null
      else -> answer
    }
  }

  companion object {
    @JvmStatic
    fun clearAllCaches() {
      ourPrevMaps.clear()
      ourInstances.forEach { it.myCachedData.clear() }
      ourInstances.clear()
    }

    @JvmStatic
    fun customize(context: AsyncDataContext, provider: Any?): AsyncDataContext {
      if (provider == null) return context
      val sink = MySink()
      cacheProviderData(sink, provider)
      val snapshot = sink.uiSnapshot?.build()
      if (snapshot == null) return context
      return AsyncDataContext { dataId: String ->
        DataManager.getInstance().getCustomizedData(dataId, context) { snapshot[it] }
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
    cachedData: CachedData,
    userData: AtomicReference<KeyFMap>,
    dataManager: DataManagerImpl,
    dataKeysCount: Int
  ) : PreCachedDataContext(compRef, cachedData, userData, dataManager, dataKeysCount) {
    override fun getDataInner(dataId: String, rulesAllowed: Boolean, ruleValuesAllowed: Boolean): Any? {
      return InjectedDataKeys.getInjectedData(dataId) { dataId ->
        super.getDataInner(dataId, rulesAllowed, ruleValuesAllowed)
      }
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

private fun cacheComponentsData(sink: MySink, isDisplayable: Boolean, components: List<Component>): CachedData {
  if (components.isEmpty()) {
    return CachedData(isDisplayable, sink.uiSnapshot?.build() ?: persistentMapOf())
  }
  lateinit var cachedData: CachedData
  val start = System.currentTimeMillis()
  for (comp in components) {
    val dataProvider = if (comp is UiDataProvider) comp else DataManagerImpl.getDataProviderEx(comp)
    hideParentEditorIfNeeded(sink, comp)
    cacheProviderData(sink, dataProvider)
    cachedData = CachedData(isDisplayable, sink.uiSnapshot?.build() ?: persistentMapOf())
    ourPrevMaps[comp] = cachedData
  }
  val time = System.currentTimeMillis() - start
  @Suppress("ControlFlowWithEmptyBody")
  if (time > 200) {
    // nothing
  }
  return cachedData
}

private fun cacheProviderData(sink: MySink, dataProvider: Any?) {
  sink.closed = false
  try {
    DataSink.uiDataSnapshot(sink, dataProvider)
  }
  finally {
    sink.closed = true
  }
}

private fun hideParentEditorIfNeeded(sink: MySink, component: Component?) {
  if (component !is JTextComponent || component is EditorComponentImpl) return
  val map = sink.uiSnapshot ?: persistentHashMapOf<String, Any>().builder().also { sink.uiSnapshot = it }
  map[CommonDataKeys.EDITOR.name] = CustomizedDataContext.EXPLICIT_NULL
}

private fun runSnapshotRules(sink: MySink, component: Component?, data: CachedData) {
  val snapshot: DataSnapshot = object : DataSnapshot {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(key: DataKey<T>): T? {
      if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT) return component as T?
      val answer = data.uiSnapshot[key.name]
      return when {
        answer === CustomizedDataContext.EXPLICIT_NULL -> null
        answer != null -> answer as T
        else -> null
      }
    }
  }
  UiDataRule.forEachRule { rule ->
    val prev = sink.source
    sink.closed = false
    sink.source = rule
    sink.uiComputed = data.uiComputed
    try {
      rule.uiDataSnapshot(sink, snapshot)
    }
    finally {
      sink.uiComputed = null
      sink.source = prev
      sink.closed = true
    }
  }
}

private class MySink : DataSink {
  var uiSnapshot: PersistentMap.Builder<String, Any>? = null
  var uiComputed: ConcurrentHashMap<String, Any>? = null

  var source: Any? = null

  var keys: Array<DataKey<*>>? = null

  var closed: Boolean = true

  private fun checkClosed() {
    assert(!closed) { "Closed sink must not be used" }
  }

  override fun <T : Any> set(key: DataKey<T>, data: T?) {
    checkClosed()
    if (data == null) return
    if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
        key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
        key == PlatformDataKeys.MODALITY_STATE) {
      return
    }
    val validated = when {
      data === CustomizedDataContext.EXPLICIT_NULL -> data
      key == BGT_DATA_PROVIDER -> {
        if (data !is DataProvider) {
          throw IllegalArgumentException("BGT_DATA_PROVIDER must be a DataProvider")
        }
        @Suppress("UNCHECKED_CAST")
        val lazyKey = (data as? MyLazyBase<*, *>)?.key as? DataKey<Any>
        if (lazyKey == BGT_DATA_PROVIDER) {
          throw IllegalArgumentException("BGT_DATA_PROVIDER must not be lazy")
        }
        // drop the existing value from the snapshot (it would always win)
        // and combine it as "existing lazy" with the incoming lazy provider
        val compositeData = if (lazyKey != null && uiComputed == null) {
          uiSnapshot?.remove(lazyKey.name)?.let { snapshotValue ->
            CompositeDataProvider.compose(data, MyLazy(lazyKey) { snapshotValue })
          }
        } else null
        val existing = (uiComputed?.get(key.name) ?: uiSnapshot?.get(key.name)) as DataProvider?
        composeKeyedProvider(lazyKey, compositeData ?: data, existing, uiComputed == null)
      }
      else -> DataValidators.validOrNull(data, key.name, source!!)
    }
    if (validated == null) return
    val map = uiComputed ?: uiSnapshot ?: persistentHashMapOf<String, Any>().builder().also { uiSnapshot = it }
    if (uiComputed != null && key != BGT_DATA_PROVIDER) {
      // rules must not override other rules or snapshot values
      if (uiSnapshot?.get(key.name) != null || map[key.name] != null) {
        return
      }
    }
    map[key.name] = validated
    if (key == CommonDataKeys.EDITOR && validated !== CustomizedDataContext.EXPLICIT_NULL) {
      map[CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.name] = validated
    }
  }

  override fun <T : Any> setNull(key: DataKey<T>) {
    checkClosed()
    if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
        key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
        key == PlatformDataKeys.MODALITY_STATE) {
      return
    }
    val map = uiSnapshot ?: persistentHashMapOf<String, Any>().builder().also { uiSnapshot = it }
    map[key.name] = CustomizedDataContext.EXPLICIT_NULL
  }

  override fun <T : Any> lazy(key: DataKey<T>, data: () -> T?) {
    set(BGT_DATA_PROVIDER, MyLazy(key, data))
  }

  override fun <T : Any> lazyNull(key: DataKey<T>) {
    set(BGT_DATA_PROVIDER, MyLazyNull(key))
  }

  override fun <T : Any> lazyValue(key: DataKey<T>, data: (DataMap) -> T?) {
    set(BGT_DATA_PROVIDER, MyLazyValue(key, data))
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

private fun composeKeyedProvider(
  lazyKey: DataKey<*>?,
  data: DataProvider,
  existing: DataProvider?,
  toFront: Boolean
): DataProvider = when {
  existing == null && lazyKey == null -> {
    data
  }
  existing !is KeyedDataProvider && lazyKey != null -> {
    KeyedDataProvider(persistentHashMapOf<String, DataProvider>().put(lazyKey.name, data), existing)
  }
  existing is KeyedDataProvider && lazyKey == null -> {
    val first = if (toFront) data else existing
    val second = if (toFront) existing else data
    KeyedDataProvider(existing.map, CompositeDataProvider.compose(first, second))
  }
  existing is KeyedDataProvider && lazyKey != null -> {
    val existingByKey = existing.map[lazyKey.name]
    val first = if (toFront) data else existingByKey
    val second = if (toFront) existingByKey else data
    KeyedDataProvider((existing.map as PersistentMap).put(
      lazyKey.name, if (first == null) second!! else CompositeDataProvider.compose(first, second)), existing.generic)
  }
  else -> {
    val first = if (toFront) data else existing!!
    val second = if (toFront) existing else data
    KeyedDataProvider(persistentHashMapOf(), CompositeDataProvider.compose(first, second))
  }
}

private sealed class MyLazyBase<T, V: Any>(val key: DataKey<T>, val supplier: V) : DataProvider, DataValidators.SourceWrapper {
  inline fun ifMyOrNull(dataId: String, block: () -> Any?): Any? = when {
    key.`is`(dataId) -> block()
    else -> null
  }
  override fun unwrapSource(): Any = supplier
  override fun toString(): String = "Lazy(${key.name})"
}

private class MyLazyNull<T>(key: DataKey<T>) : MyLazyBase<T, Any>(key, Unit) {
  override fun getData(dataId: String): Any? = ifMyOrNull(dataId) {
    CustomizedDataContext.EXPLICIT_NULL
  }
}

private class MyLazy<T>(key: DataKey<T>, supplier: () -> T?) : MyLazyBase<T, () -> T?>(key, supplier) {
  override fun getData(dataId: String): Any? = ifMyOrNull(dataId) {
    supplier()
  }
}

private class MyLazyValue<T>(key: DataKey<T>, supplier: (DataMap) -> T?) : MyLazyBase<T, (DataMap) -> T?>(key, supplier), ParametrizedDataProvider {
  override fun getData(dataId: String): Any? = throw UnsupportedOperationException()
  override fun getData(dataId: String, dataProvider: DataProvider): Any? = ifMyOrNull(dataId) {
    supplier.invoke(dataProvider.toDataMap())
  }
}

private fun DataProvider.toDataMap() = object : DataMap {
  override fun <T : Any> get(key: DataKey<T>): T? = key.getData(this@toDataMap)
}

internal fun wrapUnsafeData(data: Any?): Any? = when {
  data is IdeView -> safeIdeView(data)
  else -> data
}

private class CachedData(
  val isDisplayable: Boolean,
  uiSnapshot: PersistentMap<String, Any>
) : DataProvider, DataValidators.SourceWrapper {
  @Volatile
  var uiSnapshot: PersistentMap<String, Any> = uiSnapshot
    private set
  val uiComputed = ConcurrentHashMap<String, Any>()
  val bgtComputed = ConcurrentHashMap<String, Any>()

  // to avoid lots of nulls in maps
  val nullsByRules = ConcurrentBitSet.create()

  fun uiData(dataId: String): Any? = when {
    BGT_DATA_PROVIDER.`is`(dataId) -> uiComputed[dataId] ?: uiSnapshot[dataId]
    else -> uiSnapshot[dataId] ?: uiComputed[dataId]
  }

  fun clear() {
    uiSnapshot = persistentHashMapOf()
    uiComputed.clear()
    bgtComputed.clear()
  }

  override fun getData(dataId: String): Any? = uiData(dataId)

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