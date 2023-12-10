// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data

class GithubContent(val name: String,
                    val path: String,
                    val size: Long,
                    val url: String,
                    val downloadUrl: String) {
}