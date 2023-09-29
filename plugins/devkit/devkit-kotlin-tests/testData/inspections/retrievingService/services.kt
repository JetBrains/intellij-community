@file:JvmName("ServicesKt")

package com.intellij.openapi.components

inline fun <reified T : Any> ComponentManager.service(): T { /* compiled code */ }
inline fun <reified T : Any> ComponentManager.serviceIfCreated(): T? { /* compiled code */ }
inline fun <reified T : Any> ComponentManager.serviceOrNull(): T? { /* compiled code */ }

