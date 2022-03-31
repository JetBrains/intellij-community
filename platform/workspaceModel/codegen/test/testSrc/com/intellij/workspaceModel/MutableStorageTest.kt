import com.intellij.workspace.model.testing.*
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.jetbrains.deft.IntellijWsTest.modifyEntity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MutableStorageTest {
    @Test
    fun `simple entity mutation test`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val sampleEntity = SampleEntity {
            entitySource = MySource
            data = "ParentData"
        }

        builder.addEntity(sampleEntity)
        val simpleEntityFromStore = builder.entities(SampleEntity::class.java).single()
        builder.modifyEntity(sampleEntity) {
            entitySource = AnotherSource
            data = "NewParentData"
        }
        assertEquals(AnotherSource, sampleEntity.entitySource)
        assertEquals(AnotherSource, simpleEntityFromStore.entitySource)
        assertEquals("NewParentData", sampleEntity.data)
        assertEquals("NewParentData", simpleEntityFromStore.data)

        val newBuilder = WorkspaceEntityStorageBuilder.from(builder.toStorage())

        val entityFromStoreOne = newBuilder.entities(SampleEntity::class.java).single()
        entityFromStoreOne as SampleEntityImpl.Builder
        val entityFromStoreTwo = newBuilder.entities(SampleEntity::class.java).single()
        entityFromStoreTwo as SampleEntityImpl.Builder

        newBuilder.modifyEntity(entityFromStoreOne) {
            entitySource = MySource
            data = "ParentData"
        }

        assertEquals(AnotherSource, sampleEntity.entitySource)
        assertEquals(AnotherSource, simpleEntityFromStore.entitySource)
        assertEquals(MySource, entityFromStoreOne.entitySource)
        assertEquals(MySource, entityFromStoreTwo.entitySource)
        assertEquals("NewParentData", sampleEntity.data)
        assertEquals("NewParentData", simpleEntityFromStore.data)
        assertEquals("ParentData", entityFromStoreOne.data)
        assertEquals("ParentData", entityFromStoreTwo.data)
    }

    @Test
    fun `check exception if request data from entity which was removed`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val sampleEntity = SampleEntity {
            entitySource = MySource
            data = "ParentData"
        }
        builder.addEntity(sampleEntity)
        val newBuilder = WorkspaceEntityStorageBuilder.from(builder.toStorage())
        val entityFromStore = newBuilder.entities(SampleEntity::class.java).single()
        newBuilder.removeEntity(entityFromStore)

        assertThrows<IllegalStateException> { entityFromStore.data }
        assertEquals("ParentData", sampleEntity.data)
    }

    @Test
    fun `check parent updates`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val parentEntity = ParentMultipleEntity {
            entitySource = MySource
            parentData = "ParentData"
            children = listOf(ChildMultipleEntity {
                this.entitySource = MySource
                this.childData = "ChildOneData"
            })
        }
        builder.addEntity(parentEntity)

        val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
        val child = ChildMultipleEntity {
            entitySource = MySource
            childData = "ChildData"
            this.parentEntity = parentEntityFromStore
        }
        builder.addEntity(child)

        val childEntity = builder.entities(ChildMultipleEntity::class.java).single{ it.childData == "ChildData" }
        assertEquals(parentEntity, childEntity.parentEntity)
        assertEquals(parentEntityFromStore, childEntity.parentEntity)

        builder.modifyEntity(parentEntityFromStore) {
            parentData = "AnotherParentData"
        }
        assertEquals("AnotherParentData", parentEntityFromStore.parentData)
        assertEquals("AnotherParentData", parentEntity.parentData)
        assertEquals(2, parentEntity.children.size)
        assertEquals(2, parentEntityFromStore.children.size)
    }

    @Test
    fun `fields modification without lambda not allowed test`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val parentEntity = ParentMultipleEntity {
            entitySource = MySource
            parentData = "ParentData"
            children = listOf(ChildMultipleEntity {
                this.entitySource = MySource
                this.childData = "ChildOneData"
            })
        }
        builder.addEntity(parentEntity)

        val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
        parentEntityFromStore as ParentMultipleEntityImpl.Builder
        assertThrows<IllegalStateException> {
            parentEntityFromStore.parentData = "AnotherParentData"
        }
        assertEquals("ParentData", parentEntityFromStore.parentData)
        assertEquals("ParentData", parentEntity.parentData)

        assertThrows<IllegalStateException> {
            parentEntityFromStore.children = listOf(ChildMultipleEntity {
                this.entitySource = MySource
                this.childData = "ChildTwoData"
            })
        }
    }
}