import com.intellij.workspaceModel.storage.createEmptyBuilder
import model.ext.AttachedEntity
import model.ext.MainEntity
import model.ext.child
import org.jetbrains.deft.IntellijWsTestIjExt.modifyEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.deft.IntellijWsTestIjExt.child

class ExtensionTest {
    @Test
    fun `access by extension`() {
        val builder = createEmptyBuilder()
        builder.addEntity(AttachedEntity {
            this.entitySource = MySource
            data = "xyz"
            ref = MainEntity {
                this.entitySource = MySource
                this.x = "123"
            }
        })
        val child: AttachedEntity = builder.toStorage().entities(MainEntity::class.java).single().child!!
        assertEquals("xyz", child.data)

        assertTrue(builder.entities(MainEntity::class.java).toList().isNotEmpty())
        assertTrue(builder.entities(AttachedEntity::class.java).toList().isNotEmpty())
    }

    @Test
    fun `access by extension without builder`() {
        val entity = AttachedEntity {
            this.entitySource = MySource
            data = "xyz"
            ref = MainEntity {
                this.entitySource = MySource
                this.x = "123"
            }
        }

        assertEquals("xyz", entity.data)
        val ref = entity.ref
        val children = ref.child!!
        assertEquals("xyz", children.data)
    }

    @Test
    fun `access by extension opposite`() {
        val builder = createEmptyBuilder()
        builder.addEntity(MainEntity {
            this.x = "123"
            this.entitySource = MySource
            this.child = AttachedEntity {
                this.entitySource = MySource
                data = "xyz"
            }
        })
        val child: AttachedEntity = builder.toStorage().entities(MainEntity::class.java).single().child!!
        assertEquals("xyz", child.data)

        assertTrue(builder.entities(MainEntity::class.java).toList().isNotEmpty())
        assertTrue(builder.entities(AttachedEntity::class.java).toList().isNotEmpty())
    }

    @Test
    fun `access by extension opposite in builder`() {
        val builder = createEmptyBuilder()
        val entity = MainEntity {
            this.x = "123"
            this.entitySource = MySource
            this.child = AttachedEntity {
                this.entitySource = MySource
                data = "xyz"
            }
        }
        builder.addEntity(entity)
        assertEquals("xyz", entity.child!!.data)
    }

    @Test
    fun `access by extension opposite in modification`() {
        val builder = createEmptyBuilder()
        val entity = MainEntity {
            this.x = "123"
            this.entitySource = MySource
            this.child = AttachedEntity {
                this.entitySource = MySource
                data = "xyz"
            }
        }
        builder.addEntity(entity)
        val anotherChild = AttachedEntity {
            this.entitySource = MySource
            data = "abc"
        }

        builder.modifyEntity(entity) {
            assertEquals("xyz", this.child!!.data)
            this.child = anotherChild
        }

        assertTrue(builder.entities(MainEntity::class.java).toList().isNotEmpty())
        assertEquals("abc", builder.entities(AttachedEntity::class.java).single().data)
    }

    @Test
    fun `access by extension opposite without builder`() {
        val entity = MainEntity {
            this.x = "123"
            this.entitySource = MySource
            this.child = AttachedEntity {
                this.entitySource = MySource
                data = "xyz"
            }
        }

        assertEquals("123", entity.x)
        val ref = entity.child!!
        val children = ref.ref
        assertEquals("123", children.x)
    }
}
