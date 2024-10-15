// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.FqNameUnsafe

interface NotPropertiesService {
    fun getNotProperties(element: PsiElement): Set<FqNameUnsafe>

    companion object {
        @ApiStatus.Internal
        val DEFAULT: List<String> = ArrayList<String>().apply {
            add("java.net.Socket.getInputStream")
            add("java.net.Socket.getOutputStream")
            add("java.net.URLConnection.getInputStream")
            add("java.net.URLConnection.getOutputStream")

            val atomicMethods = listOf("getAndIncrement", "getAndDecrement", "getAcquire", "getOpaque", "getPlain")

            for (atomicClass in listOf("AtomicInteger", "AtomicLong")) {
                for (atomicMethod in atomicMethods) {
                    add("java.util.concurrent.atomic.$atomicClass.$atomicMethod")
                }
            }
            for (byteBufferMethod in listOf("getChar", "getDouble", "getFloat", "getInt", "getLong", "getShort")) {
                add("java.nio.ByteBuffer.$byteBufferMethod")
            }

            add("java.util.AbstractCollection.isEmpty") // KTIJ-31157
            add("java.util.AbstractMap.isEmpty") // KTIJ-31157
        }

        fun getNotProperties(element: PsiElement): Set<FqNameUnsafe> {
            val notProperties = element.project.serviceOrNull<NotPropertiesService>()
                ?: return DEFAULT.mapTo(LinkedHashSet()) { FqNameUnsafe(it) }

            return notProperties.getNotProperties(element)
        }
    }
}