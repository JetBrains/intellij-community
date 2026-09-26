// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.jetbrains.annotations.ApiStatus
import java.io.DataInput
import java.io.DataOutput

@ApiStatus.Internal
object EnumeratorLongDescriptor: KeyDescriptor<Long> {

    override fun getHashCode(value: Long): Int = value.hashCode()

    override fun isEqual(first: Long, second: Long): Boolean = first == second

    override fun save(out: DataOutput, value: Long): Unit = out.writeLong(value)

    override fun read(`in`: DataInput): Long = `in`.readLong()
}