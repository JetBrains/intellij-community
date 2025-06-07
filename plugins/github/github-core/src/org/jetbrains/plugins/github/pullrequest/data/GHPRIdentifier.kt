// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import kotlinx.serialization.Serializable

@Serializable
data class GHPRIdentifier(val id: String, val number: Long)