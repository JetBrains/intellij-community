package org.jetbrains.deft.IntellijWs

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.model.api.AnotherTest
import com.intellij.workspaceModel.storage.entities.model.api.FooEntity




fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AnotherTest, modification: AnotherTest.Builder.() -> Unit) = modifyEntity(AnotherTest.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FooEntity, modification: FooEntity.Builder.() -> Unit) = modifyEntity(FooEntity.Builder::class.java, entity, modification)
