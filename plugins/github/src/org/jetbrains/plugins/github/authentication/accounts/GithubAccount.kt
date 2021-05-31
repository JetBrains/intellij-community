// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.collaboration.auth.ServerAccount
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.GithubServerPath

@Tag("account")
class GithubAccount(
  @set:Transient
  @NlsSafe
  @Attribute("name")
  override var name: String = "",
  @Property(style = Property.Style.ATTRIBUTE, surroundWithTag = false)
  override val server: GithubServerPath = GithubServerPath(),
  @Attribute("id")
  @VisibleForTesting
  override val id: String = generateId())
  : ServerAccount() {

  override fun toString(): String = "$server/$name"
}