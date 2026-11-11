// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.graphql

import com.intellij.collaboration.api.dto.GraphQLErrorDTO

/**
 * GitHub returns additional fields [type] and [code] here contrary to the spec
 */
class GHGQLError @JvmOverloads constructor(message: String, val type: String?, val code: String? = null) : GraphQLErrorDTO(message) {
  override fun toString(): String = message
}
