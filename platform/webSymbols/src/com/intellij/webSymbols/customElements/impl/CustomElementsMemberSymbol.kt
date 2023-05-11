// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.json.*

class CustomElementsMemberSymbol private constructor(
  name: String,
  member: CustomElementsMember,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionWithSourceSymbol<CustomElementsMember>(name, member, origin) {

  override val namespace: SymbolNamespace
    get() = WebSymbol.NAMESPACE_JS

  override val kind: SymbolKind
    get() = WebSymbol.KIND_JS_PROPERTIES

  override val type: Any?
    get() = if (contribution is ClassMethod)
      origin.typeSupport?.resolve(contribution.buildFunctionType())
    else
      super.type

  override val defaultValue: String?
    get() = (contribution as? ClassField)?.default

  companion object {
    fun create(member: MemberBase, origin: CustomElementsJsonOrigin): CustomElementsMemberSymbol? {
      if (member !is CustomElementsMember) return null
      if (member.privacy.let { it == ClassField.Privacy.PRIVATE || it == ClassField.Privacy.PROTECTED }
          || member.static == true)
        return null
      val name = member.name ?: return null
      return CustomElementsMemberSymbol(name, member, origin)
    }
  }

}