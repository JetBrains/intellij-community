// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs

import com.intellij.openapi.vcs.impl.projectlevelman.VcsMappingsApiImpl
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffApi
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewInclusionModelApi
import com.intellij.platform.vcs.impl.shared.rpc.PartialChangesApi
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfActionsApi
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfApi
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingsApi
import com.intellij.vcs.changes.ChangeListsApiImpl
import com.intellij.vcs.changes.ChangesViewApiImpl
import com.intellij.vcs.changes.ChangesViewDiffApiImpl
import com.intellij.vcs.changes.ChangesViewInclusionModelApiImpl
import com.intellij.vcs.changes.PartialChangesApiImpl
import com.intellij.vcs.shelf.BackendShelfActionsApi
import com.intellij.vcs.shelf.BackendShelfApi
import fleet.rpc.remoteApiDescriptor

internal class VcsRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteShelfApi>()) {
      BackendShelfApi()
    }

    remoteApi(remoteApiDescriptor<RemoteShelfActionsApi>()) {
      BackendShelfActionsApi()
    }

    remoteApi(remoteApiDescriptor<ChangeListsApi>()) {
      ChangeListsApiImpl()
    }

    remoteApi(remoteApiDescriptor<VcsMappingsApi>()) {
      VcsMappingsApiImpl()
    }

    remoteApi(remoteApiDescriptor<PartialChangesApi>()) {
      PartialChangesApiImpl()
    }

    remoteApi(remoteApiDescriptor<ChangesViewApi>()) {
      ChangesViewApiImpl()
    }

    remoteApi(remoteApiDescriptor<ChangesViewDiffApi>()) {
      ChangesViewDiffApiImpl()
    }

    remoteApi(remoteApiDescriptor<ChangesViewInclusionModelApi>()) {
      ChangesViewInclusionModelApiImpl()
    }

    remoteApi(remoteApiDescriptor<ChangesViewDiffApi>()) {
      ChangesViewDiffApiImpl()
    }
  }
}
