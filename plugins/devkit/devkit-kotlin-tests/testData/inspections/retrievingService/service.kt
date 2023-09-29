@file:JvmName("ServiceKt")

package com.intellij.openapi.components

inline fun <reified T : Any> service(): T { /* compiled code */ }
inline fun <reified T : Any> serviceIfCreated(): T? { /* compiled code */ }
inline fun <reified T : Any> serviceOrNull(): T? { /* compiled code */ }
