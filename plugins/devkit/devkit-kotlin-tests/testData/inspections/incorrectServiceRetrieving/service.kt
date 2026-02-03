@file:JvmName("ServiceKt")

package com.intellij.openapi.components

inline fun <reified T : Any> service(): T = serviceOrNull<T>()!!
inline fun <reified T : Any> serviceIfCreated(): T? { return null }
inline fun <reified T : Any> serviceOrNull(): T? { return null }
