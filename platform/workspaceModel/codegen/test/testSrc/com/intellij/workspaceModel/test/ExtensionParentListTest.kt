import com.intellij.workspaceModel.storage.createEmptyBuilder
import model.ext.AttachedEntityParentList
import model.ext.MainEntityParentList
import model.ext.ref
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import org.jetbrains.deft.IntellijWsTestIjExt.ref

class ExtensionParentListTest {
    @Test
    fun `access by extension without builder`() {
        val entity = AttachedEntityParentList {
            this.entitySource = MySource
            data = "xyz"
            ref = MainEntityParentList {
                this.entitySource = MySource
                this.x = "123"
            }
        }

        assertEquals("xyz", entity.data)
        val ref = entity.ref
        val children = ref!!.children
        assertEquals("xyz", children.single().data)
    }

    @Test
    fun `access by extension without builder on parent`() {
        val entity = MainEntityParentList {
            this.entitySource = MySource
            this.x = "123"
            this.children = listOf(
                AttachedEntityParentList {
                    this.entitySource = MySource
                    data = "xyz"
                }
            )
        }

        assertEquals("123", entity.x)
        val ref = entity.children.single()
        val children = ref.ref
        assertEquals("123", children!!.x)
    }

    @Test
    fun `access by extension without builder on parent with an additional children`() {
        val entity = MainEntityParentList {
            this.entitySource = MySource
            this.x = "123"
            this.children = listOf(
                AttachedEntityParentList {
                    this.entitySource = MySource
                    data = "xyz"
                }
            )
        }
        val newChild = AttachedEntityParentList {
            this.data = "abc"
            this.ref = entity
        }

        assertEquals("123", entity.x)
        val ref = entity.children.first()
        val children = ref.ref
        assertEquals("123", children!!.x)

        assertEquals(2, newChild.ref!!.children.size)
    }

    @Test
    fun `access by extension`() {
        val entity = AttachedEntityParentList {
            this.entitySource = MySource
            data = "xyz"
            ref = MainEntityParentList {
                this.entitySource = MySource
                this.x = "123"
            }
        }
        val builder = createEmptyBuilder()
        builder.addEntity(entity)

        assertEquals("xyz", entity.data)
        val ref = entity.ref
        val children = ref!!.children
        assertEquals("xyz", children.single().data)

        assertEquals("xyz", builder.entities(AttachedEntityParentList::class.java).single().data)
        assertEquals("123", builder.entities(MainEntityParentList::class.java).single().x)
        assertEquals("xyz", builder.entities(MainEntityParentList::class.java).single().children.single().data)
        assertEquals("123", builder.entities(AttachedEntityParentList::class.java).single().ref!!.x)
    }

    @Test
    fun `access by extension on parent`() {
        val entity = MainEntityParentList {
            this.entitySource = MySource
            this.x = "123"
            this.children = listOf(
                AttachedEntityParentList {
                    this.entitySource = MySource
                    data = "xyz"
                }
            )
        }
        val builder = createEmptyBuilder()
        builder.addEntity(entity)

        assertEquals("123", entity.x)
        val ref = entity.children.single()
        val children = ref.ref
        assertEquals("123", children!!.x)

        assertEquals("xyz", builder.entities(AttachedEntityParentList::class.java).single().data)
        assertEquals("123", builder.entities(MainEntityParentList::class.java).single().x)
        assertEquals("xyz", builder.entities(MainEntityParentList::class.java).single().children.single().data)
        assertEquals("123", builder.entities(AttachedEntityParentList::class.java).single().ref!!.x)
    }

    @Test
    fun `add via single children`() {
        val child = AttachedEntityParentList {
            this.entitySource = MySource
            data = "abc"
        }
        val entity = MainEntityParentList {
            this.entitySource = MySource
            this.x = "123"
            this.children = listOf(
                AttachedEntityParentList {
                    this.entitySource = MySource
                    data = "xyz"
                },
                child
            )
        }
        val builder = createEmptyBuilder()
        builder.addEntity(child)

        assertEquals("123", entity.x)
        val ref = entity.children.first()
        val children2 = ref.ref
        assertEquals("123", children2!!.x)

        assertEquals("xyz", builder.entities(AttachedEntityParentList::class.java).single { it.data == "xyz" } .data)
        assertEquals("123", builder.entities(MainEntityParentList::class.java).single().x)
        assertEquals("xyz", builder.entities(MainEntityParentList::class.java).single().children.single { it.data == "xyz" }.data)
        assertEquals("123", builder.entities(AttachedEntityParentList::class.java).single { it.data == "xyz" }.ref!!.x)
    }

    @Test
    fun `partially in builder`() {
        val entity = MainEntityParentList {
            this.entitySource = MySource
            this.x = "123"
            this.children = listOf(
                AttachedEntityParentList {
                    this.entitySource = MySource
                    data = "xyz"
                },
            )
        }
        val builder = createEmptyBuilder()
        builder.addEntity(entity)
        val children = AttachedEntityParentList {
            this.entitySource = MySource
            this.data = "abc"
            this.ref = entity
        }

        assertEquals(2, entity.children.size)

        assertEquals("xyz", entity.children.single { it.data == "xyz" }.data)
        assertEquals("abc", entity.children.single { it.data == "abc" }.data)

        assertEquals("123", children.ref!!.x)

        assertEquals("123", entity.x)
        val ref = entity.children.first()
        val children2 = ref.ref
        assertEquals("123", children2!!.x)

        assertEquals("xyz", builder.entities(AttachedEntityParentList::class.java).single { it.data == "xyz" } .data)
        assertEquals("123", builder.entities(MainEntityParentList::class.java).single().x)
        assertEquals("xyz", builder.entities(MainEntityParentList::class.java).single().children.single { it.data == "xyz" }.data)
        assertEquals("123", builder.entities(AttachedEntityParentList::class.java).single { it.data == "xyz" }.ref!!.x)
    }
}
