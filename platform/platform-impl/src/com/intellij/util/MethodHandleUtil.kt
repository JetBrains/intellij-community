// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MethodHandleUtil")

package com.intellij.util

import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

private val LOOKUP = MethodHandles.lookup()

// this method exists as we want to reuse the same LOOKUP for all usages in the platform code for performance reasons
@Internal
internal fun Class<*>.getPrivateMethod(name: String, type: MethodType): MethodHandle {
  return MethodHandles.privateLookupIn(this, LOOKUP).findVirtual(this, name, type)
}

@Internal
internal fun Class<*>.getPublicMethod(name: String, type: MethodType): MethodHandle {
  return LOOKUP.findVirtual(this, name, type)
}

@Internal
internal fun Class<*>.getPrivateField(name: String, type: Class<*>): MethodHandle {
  return MethodHandles.privateLookupIn(this, LOOKUP).findGetter(this, name, type)
}