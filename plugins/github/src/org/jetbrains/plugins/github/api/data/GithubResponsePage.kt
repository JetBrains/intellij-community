// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.intellij.collaboration.api.util.LinkHttpHeaderValue

class GithubResponsePage<T> constructor(val items: List<T>, linkHeaderValue: LinkHttpHeaderValue?) {
  val hasNext = linkHeaderValue?.nextLink != null
  val nextLink: String? = linkHeaderValue?.nextLink
}


