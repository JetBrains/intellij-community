// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import org.jetbrains.annotations.ApiStatus

/**
 * An enum specifying the type of particular client working in IDE instance.
 * Currently, there can be three different types of clients.
 * Unlike [ClientKind] which also lists popular combination of client types.
 * For example when traversing services, it could be useful to get them for a certain combination thus [ClientKind] is used.
 * Each client however specifies its [ClientType].
 */
@ApiStatus.Experimental
enum class ClientType {
  LOCAL,
  CONTROLLER,
  GUEST;

  val isLocal  get() = this == LOCAL

  val isController  get() = this == CONTROLLER

  val isGuest get() = this == GUEST

  val isOwner get() = isLocal || isController

  val isRemote get() = isController || isGuest

  fun matches(kind: ClientKind): Boolean {
    return kind == ClientKind.ALL ||
           kind == ClientKind.LOCAL && isLocal ||
           kind == ClientKind.CONTROLLER && isController ||
           kind == ClientKind.GUEST && isGuest ||
           kind == ClientKind.OWNER && isOwner ||
           kind == ClientKind.REMOTE && isRemote
  }
}