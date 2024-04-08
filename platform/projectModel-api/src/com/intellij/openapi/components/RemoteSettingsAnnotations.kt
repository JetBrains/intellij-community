// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

/**
 * Settings that are only synchronized from Host to Client
 */
annotation class HostOnlySetting

/**
 * During the initial exchange of settings snapshots, the Client version of such setting will be preferred
 */
annotation class ClientSetting

/**
 * Settings that are not synchronized between Client and Host
 */
annotation class DoNotSynchronizeSetting
