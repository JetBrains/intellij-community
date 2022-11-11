// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util


inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
    append(prefix)
    body()
    append(postfix)
}

inline fun StringBuilder.wrapTag(tag: String, crossinline body: () -> Unit) {
    wrap("<$tag>", "</$tag>", body)
}

inline fun StringBuilder.wrapTag(tag: String, params: String, crossinline body: () -> Unit) {
    wrap("<$tag $params>", "</$tag>", body)
}


