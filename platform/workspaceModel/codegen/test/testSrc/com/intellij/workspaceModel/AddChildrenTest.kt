import com.intellij.workspace.model.testing.ChildMultipleEntity
import com.intellij.workspace.model.testing.ParentMultipleEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import model.testing.ChildNullableEntity
import model.testing.ParentNullableEntity
import org.jetbrains.deft.IntellijWsTest.modifyEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddChildrenTest {
    @Test
    fun `child added to the store at parent modification`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val entity = ParentNullableEntity {
            entitySource = MySource
            parentData = "ParentData"
        }
        builder.addEntity(entity)

        builder.modifyEntity(entity) {
            child = ChildNullableEntity {
                entitySource = MySource
                childData = "ChildData"
            }
        }
        assertNotNull(entity.child)
        val entities = builder.entities(ChildNullableEntity::class.java).single()
        assertEquals("ChildData", entity.child?.childData)
        assertEquals("ParentData", entities.parentEntity.parentData)
    }

    @Test
    fun `new child added to the store at list modification`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val parentEntity = ParentMultipleEntity {
            entitySource = MySource
            parentData = "ParentData"
        }

        val firstChild = ChildMultipleEntity {
            this.entitySource = MySource
            this.childData = "ChildOneData"
            this.parentEntity = parentEntity
        }
        builder.addEntity(parentEntity)

        val secondChild = ChildMultipleEntity {
            this.entitySource = MySource
            this.childData = "ChildTwoData"
            this.parentEntity = parentEntity
        }

        builder.modifyEntity(parentEntity) {
            children = listOf(firstChild, secondChild)
        }
        val children = builder.entities(ChildMultipleEntity::class.java).toList()
        assertEquals(2, children.size)
        assertEquals(2, parentEntity.children.size)
        children.forEach { assertEquals(parentEntity, it.parentEntity) }
    }

    @Test
    fun `child was removed from the store after list update`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val parentEntity = ParentMultipleEntity {
            entitySource = MySource
            parentData = "ParentData"
        }

        val firstChild = ChildMultipleEntity {
            this.entitySource = MySource
            this.childData = "ChildOneData"
            this.parentEntity = parentEntity
        }
        val secondChild = ChildMultipleEntity {
            this.entitySource = MySource
            this.childData = "ChildTwoData"
            this.parentEntity = parentEntity
        }
        builder.addEntity(parentEntity)
        val childrenFromStore = builder.entities(ChildMultipleEntity::class.java).toList()
        assertEquals(2, childrenFromStore.size)

        builder.modifyEntity(parentEntity) {
            children = listOf(firstChild)
        }
        val existingChild = builder.entities(ChildMultipleEntity::class.java).single()
        assertEquals("ChildOneData", existingChild.childData)
    }

    @Test
    fun `remove child from store at parent modification`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val entity = ParentNullableEntity {
            entitySource = MySource
            parentData = "ParentData"
            child = ChildNullableEntity {
                entitySource = MySource
                childData = "ChildData"
            }
        }
        builder.addEntity(entity)

        builder.modifyEntity(entity) {
            child = null
        }
        assertNull(entity.child)
        assertTrue(builder.entities(ChildNullableEntity::class.java).toList().isEmpty())
    }

    @Test
    fun `remove old child at parent entity update`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        val commonChild = ChildNullableEntity {
            entitySource = MySource
            childData = "ChildDataTwo"
        }
        val entity = ParentNullableEntity {
            entitySource = MySource
            parentData = "ParentData"
            child = commonChild
        }
        builder.addEntity(entity)

        val anotherParent = ParentNullableEntity {
            entitySource = MySource
            parentData = "AnotherParentData"
            child = ChildNullableEntity {
                entitySource = MySource
                childData = "ChildDataTwo"
            }
        }
        builder.addEntity(anotherParent)
        val children = builder.entities(ChildNullableEntity::class.java).toList()
        assertEquals(2, children.size)

        builder.modifyEntity(commonChild) {
            parentEntity = anotherParent
        }
        assertNull(entity.child)
        val childFromStore = builder.entities(ChildNullableEntity::class.java).single()
        assertEquals("ChildDataTwo", childFromStore.childData)
        assertEquals(anotherParent, childFromStore.parentEntity)
    }
}