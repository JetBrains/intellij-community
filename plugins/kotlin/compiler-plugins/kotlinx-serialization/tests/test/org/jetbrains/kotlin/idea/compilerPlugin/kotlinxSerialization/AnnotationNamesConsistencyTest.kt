// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.caches.lightClasses.annotations.KOTLINX_SERIALIZABLE_FQ_NAME
import org.jetbrains.kotlin.idea.caches.lightClasses.annotations.KOTLINX_SERIALIZER_FQ_NAME
import org.jetbrains.kotlinx.serialization.compiler.resolve.SerializationAnnotations


class AnnotationNamesConsistencyTest : TestCase() {
    fun testConsistency() {
        assertEquals(KOTLINX_SERIALIZABLE_FQ_NAME, SerializationAnnotations.serializableAnnotationFqName)
        assertEquals(KOTLINX_SERIALIZER_FQ_NAME, SerializationAnnotations.serializerAnnotationFqName)
    }
}
