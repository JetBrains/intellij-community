// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.util.containers.ContainerUtil.unmodifiableOrEmptyList

class CheckinHandlersManagerImpl : CheckinHandlersManager() {
  override fun getRegisteredCheckinHandlerFactories(vcses: Array<AbstractVcs>): List<BaseCheckinHandlerFactory> {
    val vcsesKeys = vcses.mapTo(mutableSetOf()) { it.keyInstanceMethod }

    return unmodifiableOrEmptyList(
      VcsCheckinHandlerFactory.EP_NAME.extensions.filter { it.key in vcsesKeys } +
      CheckinHandlerFactory.EP_NAME.extensions
    )
  }
}
