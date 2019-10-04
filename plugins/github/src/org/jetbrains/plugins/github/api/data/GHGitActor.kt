// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

class GHGitActor(val name: String?,
                      val email: String?,
                      val avatarUrl: String,
                      @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssX") val date: Date,
                      @JsonProperty("user") user: UserUrl?) {

  val url = user?.url

  class UserUrl(val url: String)
}