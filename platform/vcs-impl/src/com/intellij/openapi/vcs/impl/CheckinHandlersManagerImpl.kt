// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.util.containers.ContainerUtil.unmodifiableOrEmptyList
import com.intellij.util.containers.MultiMap

class CheckinHandlersManagerImpl : CheckinHandlersManager() {
  // Some plugins access this field using reflection
  private val myRegisteredBeforeCheckinHandlers = mutableListOf(*CheckinHandlerFactory.EP_NAME.extensions)
  private val factories get() = myRegisteredBeforeCheckinHandlers
  private val vcsFactories = MultiMap<VcsKey, VcsCheckinHandlerFactory>().apply {
    VcsCheckinHandlerFactory.EP_NAME.extensions.forEach { putValue(it.key, it) }
  }

  override fun getRegisteredCheckinHandlerFactories(vcses: Array<AbstractVcs>): List<BaseCheckinHandlerFactory> =
    unmodifiableOrEmptyList(vcses.flatMap { vcsFactories.get(it.keyInstanceMethod) } + factories)
}
