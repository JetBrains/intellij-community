// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel


interface CompilerArgumentsCacheMapper : CompilerArgumentsCacheAware {
    /**
     * Checks if considering arguments have been previously cached
     *
     * @param arg String value of compiler argument
     * @return `true` if arg exists in cache values, otherwise `false`
     */
    fun checkCached(arg: String): Boolean

    fun cacheArgument(arg: String): Int
}