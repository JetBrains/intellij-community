// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.ijws

import com.intellij.workspaceModel.storage.MutableEntityStorage
import deft.storage.codegen.lines
import org.jetbrains.deft.codegen.ijws.fields.wsCode
import org.jetbrains.deft.codegen.model.KtObjModule
import org.jetbrains.deft.codegen.utils.fileContents
import org.jetbrains.deft.codegen.utils.fqn

fun KtObjModule.Built.wsModuleCode(): String = fileContents(
  src.id.javaPackage,
  """
${extFields.sortedBy { it.name }.lines { wsCode }}

${
    typeDefs.filter { !it.abstract }.sortedBy { it.name }.lines {
      "fun ${MutableEntityStorage::class.fqn}.modifyEntity(entity: ${
        fqn(packageName, name)
      }, modification: $name.Builder.() -> Unit) = modifyEntity(${fqn(packageName, name)}.Builder::class.java, entity, modification)"
    }
  }
"""
)