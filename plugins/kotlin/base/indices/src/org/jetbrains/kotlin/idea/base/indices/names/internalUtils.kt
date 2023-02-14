// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmNameResolver
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil

internal fun readProtoPackageData(kotlinJvmBinaryClass: KotlinJvmBinaryClass): Pair<JvmNameResolver, ProtoBuf.Package>? {
    val header = kotlinJvmBinaryClass.classHeader
    val data = header.data ?: header.incompatibleData ?: return null
    val strings = header.strings ?: return null
    return JvmProtoBufUtil.readPackageDataFrom(data, strings)
}