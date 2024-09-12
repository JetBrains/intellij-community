// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rpc

import com.jetbrains.rhizomedb.EID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.util.UID

@Rpc
interface RemoteShelfApi: RemoteApi<Unit> {
}