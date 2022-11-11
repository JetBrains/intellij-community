// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object KotlinTestHelpers {
    fun getExpectedPath(path: Path, suffix: String): Path {
        val parent = path.parent

        val nameWithoutExtension = path.nameWithoutExtension
        val extension = path.extension

        if (extension.isEmpty()) {
            return parent.resolve(nameWithoutExtension + suffix)
        } else {
            return parent.resolve("$nameWithoutExtension$suffix.$extension")
        }
    }

    fun getTestRootPath(testClass: Class<*>): Path {
        var current = testClass
        while (true) {
            // @TestRoot should be defined on a top-level class
            current = current.enclosingClass ?: break
        }

        val testRootAnnotation = current.getAnnotation(TestRoot::class.java)
            ?: throw AssertionError("@${TestRoot::class.java.name} annotation must be defined on a class '${current.name}'")

        return KotlinRoot.PATH.resolve(testRootAnnotation.value)
    }
}