// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.json

interface CustomElementClassOrMixinDeclaration : CustomElementsContributionWithSource {
  val attributes: List<Attribute>

  val cssParts: List<CssPart>

  val cssProperties: List<CssCustomProperty>

  val customElement: Boolean?

  val demos: List<Demo>

  val events: List<Event>

  val members: List<MemberBase>

  val mixins: List<Reference>

  val slots: List<Slot>

  val superclass: Reference?

  val tagName: String?

}