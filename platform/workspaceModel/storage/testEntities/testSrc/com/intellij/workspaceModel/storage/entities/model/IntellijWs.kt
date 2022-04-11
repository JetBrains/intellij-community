package org.jetbrains.deft.IntellijWs

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.model.api.AnotherTest



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AnotherTest, modification: AnotherTest.Builder.() -> Unit) = modifyEntity(AnotherTest.Builder::class.java, entity, modification)
