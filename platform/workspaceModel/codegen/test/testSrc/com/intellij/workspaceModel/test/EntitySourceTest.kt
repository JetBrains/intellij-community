package com.intellij.workspaceModel.storage.impl

import AnotherSource
import MySource
import com.intellij.workspace.model.testing.ChildEntity
import com.intellij.workspace.model.testing.ParentEntity
import com.intellij.workspace.model.testing.SampleEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.toBuilder
import org.jetbrains.deft.IntellijWsTest.modifyEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EntitySourceTest {
    @Test
    fun `check entity source create`() {
        val mainBuilder = WorkspaceEntityStorageBuilder.create()

        val builder = WorkspaceEntityStorageBuilder.create()
        builder as WorkspaceEntityStorageBuilderImpl
        val entity = ParentEntity {
            entitySource = MySource
            parentData = "ParentData"
            child = ChildEntity {
                entitySource = AnotherSource
                childData = "ChildData"
            }
        }
        builder.addEntity(entity)
        val parentEntity = getSingleEntityBySource(builder, MySource, ParentEntity::class.java)
        assertEquals(MySource, parentEntity.entitySource)
        val childEntity = getSingleEntityBySource(builder, AnotherSource, ChildEntity::class.java)
        assertEquals(AnotherSource, childEntity.entitySource)

        mainBuilder.addDiff(builder)
        checkEvents(mainBuilder, ChangeEntry.AddEntity::class.java, ChangeEntry.AddEntity::class.java, eventsCount = 2)
    }

    @Test
    fun `check simple entity source update`() {
        var mainBuilder = WorkspaceEntityStorageBuilder.create()

        var builder = WorkspaceEntityStorageBuilder.create()
        builder as WorkspaceEntityStorageBuilderImpl

        // Add entity and check event is [AddEntity]
        val entity = SampleEntity {
            entitySource = MySource
            data = "Test Data"
            boolData = true
        }
        builder.addEntity(entity)
        checkEvents(builder, ChangeEntry.AddEntity::class.java)

        mainBuilder.addDiff(builder)
        checkEvents(mainBuilder, ChangeEntry.AddEntity::class.java)
        mainBuilder = WorkspaceEntityStorageBuilder.from(mainBuilder.toBuilder())
        val oldEntity = getSingleEntityBySource(mainBuilder, MySource, SampleEntity::class.java)
        assertTrue(mainBuilder.entitiesBySource { it == AnotherSource }.isEmpty())

        // Update entity source and property, check event is [ReplaceAndChangeSource]
        builder = WorkspaceEntityStorageBuilder.from(mainBuilder)
        builder as WorkspaceEntityStorageBuilderImpl
        var updatedEntity = builder.modifyEntity(entity) {
            boolData = false
            entitySource = AnotherSource
        }
        checkEvents(builder, ChangeEntry.ReplaceAndChangeSource::class.java, eventsCount = 2)
        assertTrue(oldEntity.boolData)
        assertEquals(MySource, oldEntity.entitySource)
        assertFalse(updatedEntity.boolData)
        assertEquals(AnotherSource, updatedEntity.entitySource)

        assertTrue(mainBuilder.entitiesBySource { it == AnotherSource }.isEmpty())
        mainBuilder.addDiff(builder)
        checkEvents(mainBuilder, ChangeEntry.ReplaceAndChangeSource::class.java, eventsCount = 2)
        assertTrue(mainBuilder.entitiesBySource { it == MySource }.isEmpty())
        assertTrue(mainBuilder.entitiesBySource { it == AnotherSource }.isNotEmpty())
        val newEntity = getSingleEntityBySource(mainBuilder, AnotherSource, SampleEntity::class.java)
        assertNotNull(newEntity)
        assertEquals(AnotherSource, newEntity.entitySource)

        val virtualFileUrlManager = VirtualFileUrlManagerImpl()
        val sourceUrl = virtualFileUrlManager.fromPath("/source")

        // Update only entity source and check if event is [ChangeEntitySource]
        builder = WorkspaceEntityStorageBuilder.from(mainBuilder)
        builder as WorkspaceEntityStorageBuilderImpl
        updatedEntity = builder.modifyEntity(entity) {
            entitySource = VFUEntitySource(sourceUrl)
        }
        checkEvents(builder, ChangeEntry.ChangeEntitySource::class.java, eventsCount = 1)
        assertTrue(updatedEntity.entitySource is VFUEntitySource)
        mainBuilder.addDiff(builder)
        checkEvents(mainBuilder, ChangeEntry.ReplaceAndChangeSource::class.java, eventsCount = 3)

        // Update entity property and check if event is [ReplaceEntity]
        builder = WorkspaceEntityStorageBuilder.from(mainBuilder)
        builder as WorkspaceEntityStorageBuilderImpl
        updatedEntity = builder.modifyEntity(entity) {
            data = "New Test Data"
        }
        checkEvents(builder, ChangeEntry.ReplaceEntity::class.java, eventsCount = 1)
        mainBuilder.addDiff(builder)
        checkEvents(mainBuilder, ChangeEntry.ReplaceAndChangeSource::class.java, eventsCount = 4)
    }

    private fun <T : WorkspaceEntity> getSingleEntityBySource(builder: WorkspaceEntityStorageBuilder, entitySource: EntitySource, kClass: Class<T>): T {
        val entityMap = builder.entitiesBySource { it == entitySource }[entitySource]
        assertEquals(1, entityMap?.size)

        val entities = entityMap?.get(kClass)
        assertNotNull(entities)
        assertEquals(1, entities.size)
        val entity = entities[0]
        assertNotNull(entity)
        return entity as T
    }

    private fun checkEvents(builder: WorkspaceEntityStorageBuilder, vararg eventTypes: Class<out ChangeEntry>, eventsCount: Long = 1) {
        builder as WorkspaceEntityStorageBuilderImpl
        val changeLog = builder.changeLog.changeLog
        assertEquals(eventsCount, builder.changeLog.modificationCount)
        val changeEntryList = changeLog.map { it.value }
        assertEquals(eventTypes.size, changeEntryList.size)
        changeEntryList.forEachIndexed { index, eventType ->
            assertTrue(eventTypes[index].isInstance(eventType))
        }
    }
}