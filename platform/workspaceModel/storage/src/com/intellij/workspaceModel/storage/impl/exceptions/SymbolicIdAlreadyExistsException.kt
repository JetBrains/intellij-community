// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.exceptions

import com.intellij.workspaceModel.storage.SymbolicEntityId

class SymbolicIdAlreadyExistsException(val id: SymbolicEntityId<*>) : RuntimeException(("Entity with symbolicId: $id already exist"))
