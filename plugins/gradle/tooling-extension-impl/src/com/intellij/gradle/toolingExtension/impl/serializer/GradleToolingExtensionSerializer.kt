// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.serializer

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GradleToolingExtensionSerializer {

  fun serialize(data: Any?): ByteArray

  fun deserialize(bytes: ByteArray): Any?

}