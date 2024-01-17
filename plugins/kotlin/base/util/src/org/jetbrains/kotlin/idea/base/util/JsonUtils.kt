// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

fun JsonObject.getString(name: String): String {
    return getNullableString(name) ?: throw IllegalStateException("Member with name '$name' is expected in '$this'")
}

fun JsonObject.getNullableString(name: String): String? {
    return this[name]?.asString
}

fun JsonObject.getAsStringList(memberName: String): List<String>? = getAsJsonArray(memberName)?.map { (it as JsonPrimitive).asString }

fun JsonObject.getAsJsonObjectList(memberName: String): List<JsonObject>? = getAsJsonArray(memberName)?.map { it as JsonObject }
