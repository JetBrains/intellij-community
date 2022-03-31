import com.intellij.workspace.model.testing.SampleEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SampleTest {

    @Test
    fun `entity creation`() {
        val entity = SampleEntity {
            data = "myData"
        }
        assertEquals("myData", entity.data)
    }

    @Test
    fun `check entity initialized`() {
        val builder = WorkspaceEntityStorageBuilder.create()
        var entity = SampleEntity {
            entitySource = MySource
            boolData = true
        }
        try {
            builder.addEntity(entity)
        } catch (e: IllegalStateException) {
            assertEquals("Field SampleEntity#data should be initialized", e.message)
        }

        entity = SampleEntity {
            data = "TestData"
            boolData = true
        }
        try {
            builder.addEntity(entity)
        } catch (e: IllegalStateException) {
            assertEquals("Field SampleEntity#entitySource should be initialized", e.message)
        }
    }
}
