import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import model.testing.*
import model.testing.EntityWithSoftLinks
import org.jetbrains.deft.IntellijWsTest.modifyEntity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals

class SoftLinksTest {
    @Test
    fun `links change`() {
        val builder = WorkspaceEntityStorageBuilder.create()

        val entity = OneEntityWithPersistentId {
            entitySource = MySource
            myName = "Data"
        }
        builder.addEntity(entity)
        val persistentId = entity.persistentId
        val softLinkEntity = EntityWithSoftLinks {
            entitySource = MySource
            link = persistentId
            manyLinks = listOf(persistentId)
            optionalLink = persistentId
            inContainer = Container(persistentId)
            inOptionalContainer = Container(persistentId)
            inContainerList = listOf(Container(persistentId))
            deepContainer =
                listOf(TooDeepContainer(listOf(DeepContainer(listOf(Container(persistentId)), persistentId))))

            sealedContainer = SealedContainer.BigContainer(persistentId)
            listSealedContainer = listOf(SealedContainer.SmallContainer(persistentId))

            justProperty = "Hello"
            justNullableProperty = "Hello"
            justListProperty = listOf("Hello")
        }
        builder.addEntity(softLinkEntity)

        builder.modifyEntity(entity) {
            myName = "AnotherData"
        }

        val updatedEntity = builder.entities(EntityWithSoftLinks::class.java).single()
        assertAll(
            { assertEquals("AnotherData", updatedEntity.link.name) },
            { assertEquals("AnotherData", updatedEntity.manyLinks.single().name) },
            { assertEquals("AnotherData", updatedEntity.optionalLink!!.name) },
            { assertEquals("AnotherData", updatedEntity.inContainer.id.name) },
            { assertEquals("AnotherData", updatedEntity.inOptionalContainer!!.id.name) },
            { assertEquals("AnotherData", updatedEntity.inContainerList.single().id.name) },
            {
                assertEquals(
                    "AnotherData",
                    updatedEntity.deepContainer.single().goDeeper.single().goDeep.single().id.name
                )
            },
            { assertEquals("AnotherData", (updatedEntity.sealedContainer as SealedContainer.BigContainer).id.name) },
            {
                assertEquals(
                    "AnotherData",
                    (updatedEntity.listSealedContainer.single() as SealedContainer.SmallContainer).notId.name
                )
            },
        )
    }
}