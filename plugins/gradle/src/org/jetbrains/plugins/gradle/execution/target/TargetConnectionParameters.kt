// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.value.TargetValue
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class TargetConnectionParameters(val connectionParameters: ConnectionParameters,
                                          val gradleUserHome: TargetValue<String>?) : ConnectionParameters by connectionParameters