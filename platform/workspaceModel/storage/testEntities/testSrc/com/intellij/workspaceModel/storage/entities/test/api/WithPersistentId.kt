package com.intellij.workspaceModel.storage.entities.test.api


import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Open
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.TestEntities.TestEntities






data class OnePersistentId(val name: String): PersistentEntityId<OneEntityWithPersistentId> {
    override val presentableName: String
        get() = name

    override fun toString(): String {
        return name
    }
}

data class Container(val id: OnePersistentId, val justData: String = "")
data class DeepContainer(val goDeep: List<Container>, val optionalId: OnePersistentId?)
data class TooDeepContainer(val goDeeper: List<DeepContainer>)

@Open
sealed class SealedContainer {
    data class BigContainer(val id: OnePersistentId) : SealedContainer()
    data class SmallContainer(val notId: OnePersistentId) : SealedContainer()
    data class EmptyContainer(val data: String) : SealedContainer()
    data class ContainerContainer(val container: List<Container>) : SealedContainer()
}

interface OneEntityWithPersistentId : WorkspaceEntityWithPersistentId {
    val myName: String

    override val persistentId: OnePersistentId
        get() {
            return OnePersistentId(myName)
        }


    //region generated code
    //@formatter:off
    interface Builder: OneEntityWithPersistentId, ModifiableWorkspaceEntity<OneEntityWithPersistentId>, ObjBuilder<OneEntityWithPersistentId> {
        override var myName: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<OneEntityWithPersistentId, Builder>(TestEntities, 27)
    //@formatter:on
    //endregion

}

interface EntityWithSoftLinks : WorkspaceEntity {
    val link: OnePersistentId
    val manyLinks: List<OnePersistentId>
    val optionalLink: OnePersistentId?
    val inContainer: Container
    val inOptionalContainer: Container?
    val inContainerList: List<Container>
    val deepContainer: List<TooDeepContainer>

    val sealedContainer: SealedContainer
    val listSealedContainer: List<SealedContainer>

    val justProperty: String
    val justNullableProperty: String?
    val justListProperty: List<String>


    //region generated code
    //@formatter:off
    interface Builder: EntityWithSoftLinks, ModifiableWorkspaceEntity<EntityWithSoftLinks>, ObjBuilder<EntityWithSoftLinks> {
        override var link: OnePersistentId
        override var entitySource: EntitySource
        override var manyLinks: List<OnePersistentId>
        override var optionalLink: OnePersistentId?
        override var inContainer: Container
        override var inOptionalContainer: Container?
        override var inContainerList: List<Container>
        override var deepContainer: List<TooDeepContainer>
        override var sealedContainer: SealedContainer
        override var listSealedContainer: List<SealedContainer>
        override var justProperty: String
        override var justNullableProperty: String?
        override var justListProperty: List<String>
    }
    
    companion object: ObjType<EntityWithSoftLinks, Builder>(TestEntities, 28)
    //@formatter:on
    //endregion

}
