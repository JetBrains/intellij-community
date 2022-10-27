// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.updateProjectModel
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.entities.test.api.CollectionFieldEntity
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class EntityWithCollectionTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `check events about collection modification are correct`() {
    val foo = "foo"
    val bar = "bar"
    val baz = "baz"
    val collectionEntity = CollectionFieldEntity(setOf(3, 4, 3), listOf(foo, bar), MySource)

    var events: List<EntityChange<CollectionFieldEntity>> = emptyList()
    projectModel.project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: VersionedStorageChange) {
        events = event.getChanges(CollectionFieldEntity::class.java)
      }
    })

    val model = WorkspaceModel.getInstance(projectModel.project)

    runWriteActionAndWait {
      model.updateProjectModel {
        it.addEntity(collectionEntity)
      }
    }

    assertThat(events).hasSize(1)
    assertThat(events[0]).isInstanceOf(EntityChange.Added::class.java)
    events = emptyList()

    runWriteActionAndWait {
      model.updateProjectModel {
        it.modifyEntity(collectionEntity.createReference<CollectionFieldEntity>().resolve(it)!!) {
          names.add(baz)
        }
      }
    }
    assertThat(events).hasSize(1)
    var replaceEvent = events[0]
    assertThat(replaceEvent).isInstanceOf(EntityChange.Replaced::class.java)
    assertThat(replaceEvent.oldEntity!!.names).containsExactly(foo, bar)
    assertThat(replaceEvent.newEntity!!.names).containsExactly(foo, bar, baz)
    events = emptyList()

    runWriteActionAndWait {
      model.updateProjectModel {
        it.modifyEntity(collectionEntity.createReference<CollectionFieldEntity>().resolve(it)!!) {
          names = mutableListOf(baz)
        }
      }
    }
    assertThat(events).hasSize(1)
    replaceEvent = events[0]
    assertThat(replaceEvent).isInstanceOf(EntityChange.Replaced::class.java)
    assertThat(replaceEvent.oldEntity!!.names).containsExactly(foo, bar, baz)
    assertThat(replaceEvent.newEntity!!.names).containsExactly(baz)
  }
}