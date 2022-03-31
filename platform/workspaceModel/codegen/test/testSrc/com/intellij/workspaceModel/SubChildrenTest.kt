
import com.intellij.workspace.model.testing.ChildSubEntity
import com.intellij.workspace.model.testing.ChildSubSubEntity
import com.intellij.workspace.model.testing.ParentSubEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SubChildrenTest {

    @Test
    fun `parent with child`() {
        val entity = ParentSubEntity {
            parentData = "ParentData"
            child = ChildSubEntity {
                child = ChildSubSubEntity {
                    childData = "ChildData"
                }
            }
        }

        assertEquals("ChildData", entity.child.child.childData)
    }

    @Test
    fun `parent with child in builder`() {
        val entity = ParentSubEntity {
            entitySource = MySource
            parentData = "ParentData"
            child = ChildSubEntity {
                entitySource = MySource
                child = ChildSubSubEntity {
                    entitySource = MySource
                    childData = "ChildData"
                }
            }
        }

        val builder = WorkspaceEntityStorageBuilder.create()
        builder.addEntity(entity)

        val parentEntity = builder.entities(ParentSubEntity::class.java).single()
        assertEquals("ChildData", parentEntity.child.child.childData)
    }

    @Test
    fun `parent with child in builder and accessing`() {
        val entity = ParentSubEntity {
            entitySource = MySource
            parentData = "ParentData"
            child = ChildSubEntity {
                entitySource = MySource
                child = ChildSubSubEntity {
                    entitySource = MySource
                    childData = "ChildData"
                }
            }
        }


        val builder = WorkspaceEntityStorageBuilder.create()
        builder.addEntity(entity)

        assertEquals("ChildData", entity.child.child.childData)
    }

    @Test
    fun `get parent from child`() {
        val entity = ParentSubEntity {
            entitySource = MySource
            parentData = "ParentData"
            child = ChildSubEntity {
                entitySource = MySource
                child = ChildSubSubEntity {
                    entitySource = MySource
                    childData = "ChildData"
                }
            }
        }

        val builder = WorkspaceEntityStorageBuilder.create()
        builder.addEntity(entity)

        val childEntity = builder.entities(ChildSubSubEntity::class.java).single()
        assertEquals("ParentData", childEntity.parentEntity.parentEntity.parentData)
    }
}