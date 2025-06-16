// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.annotations.Open
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.Container
import com.intellij.platform.workspace.storage.testEntities.entities.DeepSealedOne
import com.intellij.platform.workspace.storage.testEntities.entities.EntityWithSoftLinks
import com.intellij.platform.workspace.storage.testEntities.entities.OneSymbolicId
import com.intellij.platform.workspace.storage.testEntities.entities.SealedContainer
import com.intellij.platform.workspace.storage.testEntities.entities.SoftLinkReferencedChild
import com.intellij.platform.workspace.storage.testEntities.entities.TooDeepContainer

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithSoftLinksImpl(private val dataSource: EntityWithSoftLinksData) : EntityWithSoftLinks,
                                                                                          WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(
      EntityWithSoftLinks::class.java, SoftLinkReferencedChild::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false
    )

    private val connections = listOf<ConnectionId>(
      CHILDREN_CONNECTION_ID,
    )

  }

  override val link: OneSymbolicId
    get() {
      readField("link")
      return dataSource.link
    }

  override val manyLinks: List<OneSymbolicId>
    get() {
      readField("manyLinks")
      return dataSource.manyLinks
    }

  override val optionalLink: OneSymbolicId?
    get() {
      readField("optionalLink")
      return dataSource.optionalLink
    }

  override val inContainer: Container
    get() {
      readField("inContainer")
      return dataSource.inContainer
    }

  override val inOptionalContainer: Container?
    get() {
      readField("inOptionalContainer")
      return dataSource.inOptionalContainer
    }

  override val inContainerList: List<Container>
    get() {
      readField("inContainerList")
      return dataSource.inContainerList
    }

  override val deepContainer: List<TooDeepContainer>
    get() {
      readField("deepContainer")
      return dataSource.deepContainer
    }

  override val sealedContainer: SealedContainer
    get() {
      readField("sealedContainer")
      return dataSource.sealedContainer
    }

  override val listSealedContainer: List<SealedContainer>
    get() {
      readField("listSealedContainer")
      return dataSource.listSealedContainer
    }

  override val justProperty: String
    get() {
      readField("justProperty")
      return dataSource.justProperty
    }

  override val justNullableProperty: String?
    get() {
      readField("justNullableProperty")
      return dataSource.justNullableProperty
    }

  override val justListProperty: List<String>
    get() {
      readField("justListProperty")
      return dataSource.justListProperty
    }

  override val deepSealedClass: DeepSealedOne
    get() {
      readField("deepSealedClass")
      return dataSource.deepSealedClass
    }

  override val children: List<SoftLinkReferencedChild>
    get() = snapshot.extractOneToManyChildren<SoftLinkReferencedChild>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: EntityWithSoftLinksData?) :
    ModifiableWorkspaceEntityBase<EntityWithSoftLinks, EntityWithSoftLinksData>(result), EntityWithSoftLinks.Builder {
    internal constructor() : this(EntityWithSoftLinksData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity EntityWithSoftLinks is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isLinkInitialized()) {
        error("Field EntityWithSoftLinks#link should be initialized")
      }
      if (!getEntityData().isManyLinksInitialized()) {
        error("Field EntityWithSoftLinks#manyLinks should be initialized")
      }
      if (!getEntityData().isInContainerInitialized()) {
        error("Field EntityWithSoftLinks#inContainer should be initialized")
      }
      if (!getEntityData().isInContainerListInitialized()) {
        error("Field EntityWithSoftLinks#inContainerList should be initialized")
      }
      if (!getEntityData().isDeepContainerInitialized()) {
        error("Field EntityWithSoftLinks#deepContainer should be initialized")
      }
      if (!getEntityData().isSealedContainerInitialized()) {
        error("Field EntityWithSoftLinks#sealedContainer should be initialized")
      }
      if (!getEntityData().isListSealedContainerInitialized()) {
        error("Field EntityWithSoftLinks#listSealedContainer should be initialized")
      }
      if (!getEntityData().isJustPropertyInitialized()) {
        error("Field EntityWithSoftLinks#justProperty should be initialized")
      }
      if (!getEntityData().isJustListPropertyInitialized()) {
        error("Field EntityWithSoftLinks#justListProperty should be initialized")
      }
      if (!getEntityData().isDeepSealedClassInitialized()) {
        error("Field EntityWithSoftLinks#deepSealedClass should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field EntityWithSoftLinks#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field EntityWithSoftLinks#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_manyLinks = getEntityData().manyLinks
      if (collection_manyLinks is MutableWorkspaceList<*>) {
        collection_manyLinks.cleanModificationUpdateAction()
      }
      val collection_inContainerList = getEntityData().inContainerList
      if (collection_inContainerList is MutableWorkspaceList<*>) {
        collection_inContainerList.cleanModificationUpdateAction()
      }
      val collection_deepContainer = getEntityData().deepContainer
      if (collection_deepContainer is MutableWorkspaceList<*>) {
        collection_deepContainer.cleanModificationUpdateAction()
      }
      val collection_listSealedContainer = getEntityData().listSealedContainer
      if (collection_listSealedContainer is MutableWorkspaceList<*>) {
        collection_listSealedContainer.cleanModificationUpdateAction()
      }
      val collection_justListProperty = getEntityData().justListProperty
      if (collection_justListProperty is MutableWorkspaceList<*>) {
        collection_justListProperty.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as EntityWithSoftLinks
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.link != dataSource.link) this.link = dataSource.link
      if (this.manyLinks != dataSource.manyLinks) this.manyLinks = dataSource.manyLinks.toMutableList()
      if (this.optionalLink != dataSource?.optionalLink) this.optionalLink = dataSource.optionalLink
      if (this.inContainer != dataSource.inContainer) this.inContainer = dataSource.inContainer
      if (this.inOptionalContainer != dataSource?.inOptionalContainer) this.inOptionalContainer = dataSource.inOptionalContainer
      if (this.inContainerList != dataSource.inContainerList) this.inContainerList = dataSource.inContainerList.toMutableList()
      if (this.deepContainer != dataSource.deepContainer) this.deepContainer = dataSource.deepContainer.toMutableList()
      if (this.sealedContainer != dataSource.sealedContainer) this.sealedContainer = dataSource.sealedContainer
      if (this.listSealedContainer != dataSource.listSealedContainer) this.listSealedContainer =
        dataSource.listSealedContainer.toMutableList()
      if (this.justProperty != dataSource.justProperty) this.justProperty = dataSource.justProperty
      if (this.justNullableProperty != dataSource?.justNullableProperty) this.justNullableProperty = dataSource.justNullableProperty
      if (this.justListProperty != dataSource.justListProperty) this.justListProperty = dataSource.justListProperty.toMutableList()
      if (this.deepSealedClass != dataSource.deepSealedClass) this.deepSealedClass = dataSource.deepSealedClass
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var link: OneSymbolicId
      get() = getEntityData().link
      set(value) {
        checkModificationAllowed()
        getEntityData(true).link = value
        changedProperty.add("link")

      }

    private val manyLinksUpdater: (value: List<OneSymbolicId>) -> Unit = { value ->

      changedProperty.add("manyLinks")
    }
    override var manyLinks: MutableList<OneSymbolicId>
      get() {
        val collection_manyLinks = getEntityData().manyLinks
        if (collection_manyLinks !is MutableWorkspaceList) return collection_manyLinks
        if (diff == null || modifiable.get()) {
          collection_manyLinks.setModificationUpdateAction(manyLinksUpdater)
        }
        else {
          collection_manyLinks.cleanModificationUpdateAction()
        }
        return collection_manyLinks
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).manyLinks = value
        manyLinksUpdater.invoke(value)
      }

    override var optionalLink: OneSymbolicId?
      get() = getEntityData().optionalLink
      set(value) {
        checkModificationAllowed()
        getEntityData(true).optionalLink = value
        changedProperty.add("optionalLink")

      }

    override var inContainer: Container
      get() = getEntityData().inContainer
      set(value) {
        checkModificationAllowed()
        getEntityData(true).inContainer = value
        changedProperty.add("inContainer")

      }

    override var inOptionalContainer: Container?
      get() = getEntityData().inOptionalContainer
      set(value) {
        checkModificationAllowed()
        getEntityData(true).inOptionalContainer = value
        changedProperty.add("inOptionalContainer")

      }

    private val inContainerListUpdater: (value: List<Container>) -> Unit = { value ->

      changedProperty.add("inContainerList")
    }
    override var inContainerList: MutableList<Container>
      get() {
        val collection_inContainerList = getEntityData().inContainerList
        if (collection_inContainerList !is MutableWorkspaceList) return collection_inContainerList
        if (diff == null || modifiable.get()) {
          collection_inContainerList.setModificationUpdateAction(inContainerListUpdater)
        }
        else {
          collection_inContainerList.cleanModificationUpdateAction()
        }
        return collection_inContainerList
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).inContainerList = value
        inContainerListUpdater.invoke(value)
      }

    private val deepContainerUpdater: (value: List<TooDeepContainer>) -> Unit = { value ->

      changedProperty.add("deepContainer")
    }
    override var deepContainer: MutableList<TooDeepContainer>
      get() {
        val collection_deepContainer = getEntityData().deepContainer
        if (collection_deepContainer !is MutableWorkspaceList) return collection_deepContainer
        if (diff == null || modifiable.get()) {
          collection_deepContainer.setModificationUpdateAction(deepContainerUpdater)
        }
        else {
          collection_deepContainer.cleanModificationUpdateAction()
        }
        return collection_deepContainer
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).deepContainer = value
        deepContainerUpdater.invoke(value)
      }

    override var sealedContainer: SealedContainer
      get() = getEntityData().sealedContainer
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sealedContainer = value
        changedProperty.add("sealedContainer")

      }

    private val listSealedContainerUpdater: (value: List<SealedContainer>) -> Unit = { value ->

      changedProperty.add("listSealedContainer")
    }
    override var listSealedContainer: MutableList<SealedContainer>
      get() {
        val collection_listSealedContainer = getEntityData().listSealedContainer
        if (collection_listSealedContainer !is MutableWorkspaceList) return collection_listSealedContainer
        if (diff == null || modifiable.get()) {
          collection_listSealedContainer.setModificationUpdateAction(listSealedContainerUpdater)
        }
        else {
          collection_listSealedContainer.cleanModificationUpdateAction()
        }
        return collection_listSealedContainer
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).listSealedContainer = value
        listSealedContainerUpdater.invoke(value)
      }

    override var justProperty: String
      get() = getEntityData().justProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).justProperty = value
        changedProperty.add("justProperty")
      }

    override var justNullableProperty: String?
      get() = getEntityData().justNullableProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).justNullableProperty = value
        changedProperty.add("justNullableProperty")
      }

    private val justListPropertyUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("justListProperty")
    }
    override var justListProperty: MutableList<String>
      get() {
        val collection_justListProperty = getEntityData().justListProperty
        if (collection_justListProperty !is MutableWorkspaceList) return collection_justListProperty
        if (diff == null || modifiable.get()) {
          collection_justListProperty.setModificationUpdateAction(justListPropertyUpdater)
        }
        else {
          collection_justListProperty.cleanModificationUpdateAction()
        }
        return collection_justListProperty
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).justListProperty = value
        justListPropertyUpdater.invoke(value)
      }

    override var deepSealedClass: DeepSealedOne
      get() = getEntityData().deepSealedClass
      set(value) {
        checkModificationAllowed()
        getEntityData(true).deepSealedClass = value
        changedProperty.add("deepSealedClass")

      }

    // List of non-abstract referenced types
    var _children: List<SoftLinkReferencedChild>? = emptyList()
    override var children: List<SoftLinkReferencedChild.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)!!
            .toList() as List<SoftLinkReferencedChild.Builder>) +
          (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<SoftLinkReferencedChild.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<SoftLinkReferencedChild.Builder> ?: emptyList()
        }
      }
      set(value) {
        // Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<EntityWithSoftLinks> = EntityWithSoftLinks::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithSoftLinksData : WorkspaceEntityData<EntityWithSoftLinks>(), SoftLinkable {
  lateinit var link: OneSymbolicId
  lateinit var manyLinks: MutableList<OneSymbolicId>
  var optionalLink: OneSymbolicId? = null
  lateinit var inContainer: Container
  var inOptionalContainer: Container? = null
  lateinit var inContainerList: MutableList<Container>
  lateinit var deepContainer: MutableList<TooDeepContainer>
  lateinit var sealedContainer: SealedContainer
  lateinit var listSealedContainer: MutableList<SealedContainer>
  lateinit var justProperty: String
  var justNullableProperty: String? = null
  lateinit var justListProperty: MutableList<String>
  lateinit var deepSealedClass: DeepSealedOne

  internal fun isLinkInitialized(): Boolean = ::link.isInitialized
  internal fun isManyLinksInitialized(): Boolean = ::manyLinks.isInitialized
  internal fun isInContainerInitialized(): Boolean = ::inContainer.isInitialized
  internal fun isInContainerListInitialized(): Boolean = ::inContainerList.isInitialized
  internal fun isDeepContainerInitialized(): Boolean = ::deepContainer.isInitialized
  internal fun isSealedContainerInitialized(): Boolean = ::sealedContainer.isInitialized
  internal fun isListSealedContainerInitialized(): Boolean = ::listSealedContainer.isInitialized
  internal fun isJustPropertyInitialized(): Boolean = ::justProperty.isInitialized
  internal fun isJustListPropertyInitialized(): Boolean = ::justListProperty.isInitialized
  internal fun isDeepSealedClassInitialized(): Boolean = ::deepSealedClass.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(link)
    for (item in manyLinks) {
      result.add(item)
    }
    val optionalLink_optionalLink = optionalLink
    if (optionalLink_optionalLink != null) {
      result.add(optionalLink_optionalLink)
    }
    result.add(inContainer.id)
    for (item in inContainerList) {
      result.add(item.id)
    }
    for (item in deepContainer) {
      for (item in item.goDeeper) {
        for (item in item.goDeep) {
          result.add(item.id)
        }
        val optionalLink_item_optionalId = item.optionalId
        if (optionalLink_item_optionalId != null) {
          result.add(optionalLink_item_optionalId)
        }
      }
    }
    val _sealedContainer = sealedContainer
    when (_sealedContainer) {
      is SealedContainer.BigContainer -> {
        result.add(_sealedContainer.id)
      }
      is SealedContainer.ContainerContainer -> {
        for (item in _sealedContainer.container) {
          result.add(item.id)
        }
      }
      is SealedContainer.EmptyContainer -> {
      }
      is SealedContainer.SmallContainer -> {
        result.add(_sealedContainer.notId)
      }
    }
    for (item in listSealedContainer) {
      when (item) {
        is SealedContainer.BigContainer -> {
          result.add(item.id)
        }
        is SealedContainer.ContainerContainer -> {
          for (item in item.container) {
            result.add(item.id)
          }
        }
        is SealedContainer.EmptyContainer -> {
        }
        is SealedContainer.SmallContainer -> {
          result.add(item.notId)
        }
      }
    }
    for (item in justListProperty) {
    }
    val _deepSealedClass = deepSealedClass
    when (_deepSealedClass) {
      is DeepSealedOne.DeepSealedTwo -> {
        val __deepSealedClass = _deepSealedClass
        when (__deepSealedClass) {
          is DeepSealedOne.DeepSealedTwo.DeepSealedThree -> {
            val ___deepSealedClass = __deepSealedClass
            when (___deepSealedClass) {
              is DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour -> {
                result.add(___deepSealedClass.id)
              }
            }
          }
        }
      }
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, link)
    for (item in manyLinks) {
      index.index(this, item)
    }
    val optionalLink_optionalLink = optionalLink
    if (optionalLink_optionalLink != null) {
      index.index(this, optionalLink_optionalLink)
    }
    index.index(this, inContainer.id)
    for (item in inContainerList) {
      index.index(this, item.id)
    }
    for (item in deepContainer) {
      for (item in item.goDeeper) {
        for (item in item.goDeep) {
          index.index(this, item.id)
        }
        val optionalLink_item_optionalId = item.optionalId
        if (optionalLink_item_optionalId != null) {
          index.index(this, optionalLink_item_optionalId)
        }
      }
    }
    val _sealedContainer = sealedContainer
    when (_sealedContainer) {
      is SealedContainer.BigContainer -> {
        index.index(this, _sealedContainer.id)
      }
      is SealedContainer.ContainerContainer -> {
        for (item in _sealedContainer.container) {
          index.index(this, item.id)
        }
      }
      is SealedContainer.EmptyContainer -> {
      }
      is SealedContainer.SmallContainer -> {
        index.index(this, _sealedContainer.notId)
      }
    }
    for (item in listSealedContainer) {
      when (item) {
        is SealedContainer.BigContainer -> {
          index.index(this, item.id)
        }
        is SealedContainer.ContainerContainer -> {
          for (item in item.container) {
            index.index(this, item.id)
          }
        }
        is SealedContainer.EmptyContainer -> {
        }
        is SealedContainer.SmallContainer -> {
          index.index(this, item.notId)
        }
      }
    }
    for (item in justListProperty) {
    }
    val _deepSealedClass = deepSealedClass
    when (_deepSealedClass) {
      is DeepSealedOne.DeepSealedTwo -> {
        val __deepSealedClass = _deepSealedClass
        when (__deepSealedClass) {
          is DeepSealedOne.DeepSealedTwo.DeepSealedThree -> {
            val ___deepSealedClass = __deepSealedClass
            when (___deepSealedClass) {
              is DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour -> {
                index.index(this, ___deepSealedClass.id)
              }
            }
          }
        }
      }
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val removedItem_link = mutablePreviousSet.remove(link)
    if (!removedItem_link) {
      index.index(this, link)
    }
    for (item in manyLinks) {
      val removedItem_item = mutablePreviousSet.remove(item)
      if (!removedItem_item) {
        index.index(this, item)
      }
    }
    val optionalLink_optionalLink = optionalLink
    if (optionalLink_optionalLink != null) {
      val removedItem_optionalLink_optionalLink = mutablePreviousSet.remove(optionalLink_optionalLink)
      if (!removedItem_optionalLink_optionalLink) {
        index.index(this, optionalLink_optionalLink)
      }
    }
    val removedItem_inContainer_id = mutablePreviousSet.remove(inContainer.id)
    if (!removedItem_inContainer_id) {
      index.index(this, inContainer.id)
    }
    for (item in inContainerList) {
      val removedItem_item_id = mutablePreviousSet.remove(item.id)
      if (!removedItem_item_id) {
        index.index(this, item.id)
      }
    }
    for (item in deepContainer) {
      for (item in item.goDeeper) {
        for (item in item.goDeep) {
          val removedItem_item_id = mutablePreviousSet.remove(item.id)
          if (!removedItem_item_id) {
            index.index(this, item.id)
          }
        }
        val optionalLink_item_optionalId = item.optionalId
        if (optionalLink_item_optionalId != null) {
          val removedItem_optionalLink_item_optionalId = mutablePreviousSet.remove(optionalLink_item_optionalId)
          if (!removedItem_optionalLink_item_optionalId) {
            index.index(this, optionalLink_item_optionalId)
          }
        }
      }
    }
    val _sealedContainer = sealedContainer
    when (_sealedContainer) {
      is SealedContainer.BigContainer -> {
        val removedItem__sealedContainer_id = mutablePreviousSet.remove(_sealedContainer.id)
        if (!removedItem__sealedContainer_id) {
          index.index(this, _sealedContainer.id)
        }
      }
      is SealedContainer.ContainerContainer -> {
        for (item in _sealedContainer.container) {
          val removedItem_item_id = mutablePreviousSet.remove(item.id)
          if (!removedItem_item_id) {
            index.index(this, item.id)
          }
        }
      }
      is SealedContainer.EmptyContainer -> {
      }
      is SealedContainer.SmallContainer -> {
        val removedItem__sealedContainer_notId = mutablePreviousSet.remove(_sealedContainer.notId)
        if (!removedItem__sealedContainer_notId) {
          index.index(this, _sealedContainer.notId)
        }
      }
    }
    for (item in listSealedContainer) {
      when (item) {
        is SealedContainer.BigContainer -> {
          val removedItem_item_id = mutablePreviousSet.remove(item.id)
          if (!removedItem_item_id) {
            index.index(this, item.id)
          }
        }
        is SealedContainer.ContainerContainer -> {
          for (item in item.container) {
            val removedItem_item_id = mutablePreviousSet.remove(item.id)
            if (!removedItem_item_id) {
              index.index(this, item.id)
            }
          }
        }
        is SealedContainer.EmptyContainer -> {
        }
        is SealedContainer.SmallContainer -> {
          val removedItem_item_notId = mutablePreviousSet.remove(item.notId)
          if (!removedItem_item_notId) {
            index.index(this, item.notId)
          }
        }
      }
    }
    for (item in justListProperty) {
    }
    val _deepSealedClass = deepSealedClass
    when (_deepSealedClass) {
      is DeepSealedOne.DeepSealedTwo -> {
        val __deepSealedClass = _deepSealedClass
        when (__deepSealedClass) {
          is DeepSealedOne.DeepSealedTwo.DeepSealedThree -> {
            val ___deepSealedClass = __deepSealedClass
            when (___deepSealedClass) {
              is DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour -> {
                val removedItem____deepSealedClass_id = mutablePreviousSet.remove(___deepSealedClass.id)
                if (!removedItem____deepSealedClass_id) {
                  index.index(this, ___deepSealedClass.id)
                }
              }
            }
          }
        }
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val link_data = if (link == oldLink) {
      changed = true
      newLink as OneSymbolicId
    }
    else {
      null
    }
    if (link_data != null) {
      link = link_data
    }
    val manyLinks_data = manyLinks.map {
      val it_data = if (it == oldLink) {
        changed = true
        newLink as OneSymbolicId
      }
      else {
        null
      }
      if (it_data != null) {
        it_data
      }
      else {
        it
      }
    }
    if (manyLinks_data != null) {
      manyLinks = manyLinks_data as MutableList<OneSymbolicId>
    }
    var optionalLink_data_optional = if (optionalLink != null) {
      val optionalLink___data = if (optionalLink!! == oldLink) {
        changed = true
        newLink as OneSymbolicId
      }
      else {
        null
      }
      optionalLink___data
    }
    else {
      null
    }
    if (optionalLink_data_optional != null) {
      optionalLink = optionalLink_data_optional
    }
    val inContainer_id_data = if (inContainer.id == oldLink) {
      changed = true
      newLink as OneSymbolicId
    }
    else {
      null
    }
    var inContainer_data = inContainer
    if (inContainer_id_data != null) {
      inContainer_data = inContainer_data.copy(id = inContainer_id_data)
    }
    if (inContainer_data != null) {
      inContainer = inContainer_data
    }
    var inOptionalContainer_data_optional = if (inOptionalContainer != null) {
      val inOptionalContainer___id_data = if (inOptionalContainer!!.id == oldLink) {
        changed = true
        newLink as OneSymbolicId
      }
      else {
        null
      }
      var inOptionalContainer___data = inOptionalContainer!!
      if (inOptionalContainer___id_data != null) {
        inOptionalContainer___data = inOptionalContainer___data.copy(id = inOptionalContainer___id_data)
      }
      inOptionalContainer___data
    }
    else {
      null
    }
    if (inOptionalContainer_data_optional != null) {
      inOptionalContainer = inOptionalContainer_data_optional
    }
    val inContainerList_data = inContainerList.map {
      val it_id_data = if (it.id == oldLink) {
        changed = true
        newLink as OneSymbolicId
      }
      else {
        null
      }
      var it_data = it
      if (it_id_data != null) {
        it_data = it_data.copy(id = it_id_data)
      }
      if (it_data != null) {
        it_data
      }
      else {
        it
      }
    }
    if (inContainerList_data != null) {
      inContainerList = inContainerList_data as MutableList<Container>
    }
    val deepContainer_data = deepContainer.map {
      val it_goDeeper_data = it.goDeeper.map {
        val it_goDeep_data = it.goDeep.map {
          val it_id_data = if (it.id == oldLink) {
            changed = true
            newLink as OneSymbolicId
          }
          else {
            null
          }
          var it_data = it
          if (it_id_data != null) {
            it_data = it_data.copy(id = it_id_data)
          }
          if (it_data != null) {
            it_data
          }
          else {
            it
          }
        }
        var it_optionalId_data_optional = if (it.optionalId != null) {
          val it_optionalId___data = if (it.optionalId!! == oldLink) {
            changed = true
            newLink as OneSymbolicId
          }
          else {
            null
          }
          it_optionalId___data
        }
        else {
          null
        }
        var it_data = it
        if (it_goDeep_data != null) {
          it_data = it_data.copy(goDeep = it_goDeep_data)
        }
        if (it_optionalId_data_optional != null) {
          it_data = it_data.copy(optionalId = it_optionalId_data_optional)
        }
        if (it_data != null) {
          it_data
        }
        else {
          it
        }
      }
      var it_data = it
      if (it_goDeeper_data != null) {
        it_data = it_data.copy(goDeeper = it_goDeeper_data)
      }
      if (it_data != null) {
        it_data
      }
      else {
        it
      }
    }
    if (deepContainer_data != null) {
      deepContainer = deepContainer_data as MutableList<TooDeepContainer>
    }
    val _sealedContainer = sealedContainer
    val res_sealedContainer = when (_sealedContainer) {
      is SealedContainer.BigContainer -> {
        val _sealedContainer_id_data = if (_sealedContainer.id == oldLink) {
          changed = true
          newLink as OneSymbolicId
        }
        else {
          null
        }
        var _sealedContainer_data = _sealedContainer
        if (_sealedContainer_id_data != null) {
          _sealedContainer_data = _sealedContainer_data.copy(id = _sealedContainer_id_data)
        }
        _sealedContainer_data
      }
      is SealedContainer.ContainerContainer -> {
        val _sealedContainer_container_data = _sealedContainer.container.map {
          val it_id_data = if (it.id == oldLink) {
            changed = true
            newLink as OneSymbolicId
          }
          else {
            null
          }
          var it_data = it
          if (it_id_data != null) {
            it_data = it_data.copy(id = it_id_data)
          }
          if (it_data != null) {
            it_data
          }
          else {
            it
          }
        }
        var _sealedContainer_data = _sealedContainer
        if (_sealedContainer_container_data != null) {
          _sealedContainer_data = _sealedContainer_data.copy(container = _sealedContainer_container_data)
        }
        _sealedContainer_data
      }
      is SealedContainer.EmptyContainer -> {
        _sealedContainer
      }
      is SealedContainer.SmallContainer -> {
        val _sealedContainer_notId_data = if (_sealedContainer.notId == oldLink) {
          changed = true
          newLink as OneSymbolicId
        }
        else {
          null
        }
        var _sealedContainer_data = _sealedContainer
        if (_sealedContainer_notId_data != null) {
          _sealedContainer_data = _sealedContainer_data.copy(notId = _sealedContainer_notId_data)
        }
        _sealedContainer_data
      }
    }
    if (res_sealedContainer != null) {
      sealedContainer = res_sealedContainer
    }
    val listSealedContainer_data = listSealedContainer.map {
      val _it = it
      val res_it = when (_it) {
        is SealedContainer.BigContainer -> {
          val _it_id_data = if (_it.id == oldLink) {
            changed = true
            newLink as OneSymbolicId
          }
          else {
            null
          }
          var _it_data = _it
          if (_it_id_data != null) {
            _it_data = _it_data.copy(id = _it_id_data)
          }
          _it_data
        }
        is SealedContainer.ContainerContainer -> {
          val _it_container_data = _it.container.map {
            val it_id_data = if (it.id == oldLink) {
              changed = true
              newLink as OneSymbolicId
            }
            else {
              null
            }
            var it_data = it
            if (it_id_data != null) {
              it_data = it_data.copy(id = it_id_data)
            }
            if (it_data != null) {
              it_data
            }
            else {
              it
            }
          }
          var _it_data = _it
          if (_it_container_data != null) {
            _it_data = _it_data.copy(container = _it_container_data)
          }
          _it_data
        }
        is SealedContainer.EmptyContainer -> {
          _it
        }
        is SealedContainer.SmallContainer -> {
          val _it_notId_data = if (_it.notId == oldLink) {
            changed = true
            newLink as OneSymbolicId
          }
          else {
            null
          }
          var _it_data = _it
          if (_it_notId_data != null) {
            _it_data = _it_data.copy(notId = _it_notId_data)
          }
          _it_data
        }
      }
      if (res_it != null) {
        res_it
      }
      else {
        it
      }
    }
    if (listSealedContainer_data != null) {
      listSealedContainer = listSealedContainer_data as MutableList<SealedContainer>
    }
    val _deepSealedClass = deepSealedClass
    val res_deepSealedClass = when (_deepSealedClass) {
      is DeepSealedOne.DeepSealedTwo -> {
        val __deepSealedClass = _deepSealedClass
        val res__deepSealedClass = when (__deepSealedClass) {
          is DeepSealedOne.DeepSealedTwo.DeepSealedThree -> {
            val ___deepSealedClass = __deepSealedClass
            val res___deepSealedClass = when (___deepSealedClass) {
              is DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour -> {
                val ___deepSealedClass_id_data = if (___deepSealedClass.id == oldLink) {
                  changed = true
                  newLink as OneSymbolicId
                }
                else {
                  null
                }
                var ___deepSealedClass_data = ___deepSealedClass
                if (___deepSealedClass_id_data != null) {
                  ___deepSealedClass_data = ___deepSealedClass_data.copy(id = ___deepSealedClass_id_data)
                }
                ___deepSealedClass_data
              }
            }
            res___deepSealedClass
          }
        }
        res__deepSealedClass
      }
    }
    if (res_deepSealedClass != null) {
      deepSealedClass = res_deepSealedClass
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<EntityWithSoftLinks> {
    val modifiable = EntityWithSoftLinksImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): EntityWithSoftLinks {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = EntityWithSoftLinksImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.EntityWithSoftLinks"
    ) as EntityMetadata
  }

  override fun clone(): EntityWithSoftLinksData {
    val clonedEntity = super.clone()
    clonedEntity as EntityWithSoftLinksData
    clonedEntity.manyLinks = clonedEntity.manyLinks.toMutableWorkspaceList()
    clonedEntity.inContainerList = clonedEntity.inContainerList.toMutableWorkspaceList()
    clonedEntity.deepContainer = clonedEntity.deepContainer.toMutableWorkspaceList()
    clonedEntity.listSealedContainer = clonedEntity.listSealedContainer.toMutableWorkspaceList()
    clonedEntity.justListProperty = clonedEntity.justListProperty.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return EntityWithSoftLinks::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return EntityWithSoftLinks(
      link, manyLinks, inContainer, inContainerList, deepContainer, sealedContainer, listSealedContainer, justProperty, justListProperty,
      deepSealedClass, entitySource
    ) {
      this.optionalLink = this@EntityWithSoftLinksData.optionalLink
      this.inOptionalContainer = this@EntityWithSoftLinksData.inOptionalContainer
      this.justNullableProperty = this@EntityWithSoftLinksData.justNullableProperty
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as EntityWithSoftLinksData

    if (this.entitySource != other.entitySource) return false
    if (this.link != other.link) return false
    if (this.manyLinks != other.manyLinks) return false
    if (this.optionalLink != other.optionalLink) return false
    if (this.inContainer != other.inContainer) return false
    if (this.inOptionalContainer != other.inOptionalContainer) return false
    if (this.inContainerList != other.inContainerList) return false
    if (this.deepContainer != other.deepContainer) return false
    if (this.sealedContainer != other.sealedContainer) return false
    if (this.listSealedContainer != other.listSealedContainer) return false
    if (this.justProperty != other.justProperty) return false
    if (this.justNullableProperty != other.justNullableProperty) return false
    if (this.justListProperty != other.justListProperty) return false
    if (this.deepSealedClass != other.deepSealedClass) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as EntityWithSoftLinksData

    if (this.link != other.link) return false
    if (this.manyLinks != other.manyLinks) return false
    if (this.optionalLink != other.optionalLink) return false
    if (this.inContainer != other.inContainer) return false
    if (this.inOptionalContainer != other.inOptionalContainer) return false
    if (this.inContainerList != other.inContainerList) return false
    if (this.deepContainer != other.deepContainer) return false
    if (this.sealedContainer != other.sealedContainer) return false
    if (this.listSealedContainer != other.listSealedContainer) return false
    if (this.justProperty != other.justProperty) return false
    if (this.justNullableProperty != other.justNullableProperty) return false
    if (this.justListProperty != other.justListProperty) return false
    if (this.deepSealedClass != other.deepSealedClass) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + link.hashCode()
    result = 31 * result + manyLinks.hashCode()
    result = 31 * result + optionalLink.hashCode()
    result = 31 * result + inContainer.hashCode()
    result = 31 * result + inOptionalContainer.hashCode()
    result = 31 * result + inContainerList.hashCode()
    result = 31 * result + deepContainer.hashCode()
    result = 31 * result + sealedContainer.hashCode()
    result = 31 * result + listSealedContainer.hashCode()
    result = 31 * result + justProperty.hashCode()
    result = 31 * result + justNullableProperty.hashCode()
    result = 31 * result + justListProperty.hashCode()
    result = 31 * result + deepSealedClass.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + link.hashCode()
    result = 31 * result + manyLinks.hashCode()
    result = 31 * result + optionalLink.hashCode()
    result = 31 * result + inContainer.hashCode()
    result = 31 * result + inOptionalContainer.hashCode()
    result = 31 * result + inContainerList.hashCode()
    result = 31 * result + deepContainer.hashCode()
    result = 31 * result + sealedContainer.hashCode()
    result = 31 * result + listSealedContainer.hashCode()
    result = 31 * result + justProperty.hashCode()
    result = 31 * result + justNullableProperty.hashCode()
    result = 31 * result + justListProperty.hashCode()
    result = 31 * result + deepSealedClass.hashCode()
    return result
  }
}
