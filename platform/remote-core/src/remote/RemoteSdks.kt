// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RemoteSdks")
package com.intellij.remote

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun Sdk.isBasedOnCredentialsType(credentialsType: CredentialsType<*>): Boolean {
  return (sdkAdditionalData as? RemoteSdkAdditionalData)?.remoteConnectionType == credentialsType
}
