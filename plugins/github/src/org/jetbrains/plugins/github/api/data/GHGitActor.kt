// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.fasterxml.jackson.annotation.JsonFormat
import java.util.*

class GHGitActor(val name: String?,
                 val email: String?,
                 val avatarUrl: String,
                 @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssX") val date: Date?,
                 val user: GHUser?)