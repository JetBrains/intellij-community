// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

annotation class RemoteSetting(val direction: RemoteSettingDirection, val allowedInCwm: Boolean = false)

enum class RemoteSettingDirection { FromHost, FromClient, Both, None }
// TODO remove Both and None, rename to initial value source
