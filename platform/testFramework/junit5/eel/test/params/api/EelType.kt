// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.execution.target.TargetEnvironmentConfiguration
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface EelType
sealed interface RemoteEelType

class Wsl : EelType, RemoteEelType
class Docker : EelType, RemoteEelType
data object Local : EelType

