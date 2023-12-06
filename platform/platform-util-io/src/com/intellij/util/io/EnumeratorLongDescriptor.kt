// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import java.io.DataInput
import java.io.DataOutput

object EnumeratorLongDescriptor: KeyDescriptor<Long> {

    override fun getHashCode(value: Long): Int = value.hashCode()

    override fun isEqual(first: Long, second: Long): Boolean = first == second

    override fun save(out: DataOutput, value: Long): Unit = out.writeLong(value)

    override fun read(`in`: DataInput): Long = `in`.readLong()
}