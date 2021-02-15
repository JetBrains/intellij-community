// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinUtils")
package com.intellij.util

inline fun <reified T> Any?.castSafelyTo(): T? = this as? T
