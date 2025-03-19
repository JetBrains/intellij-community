@file:JvmName("ServicesKt")

package com.intellij.openapi.components

inline fun <reified T : Any> ComponentManager.service(): T = serviceOrNull<T>()!!
inline fun <reified T : Any> ComponentManager.serviceIfCreated(): T? = null
inline fun <reified T : Any> ComponentManager.serviceOrNull(): T? = null

