// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.polySymbols.impl.PolySymbolAccessModifierData.Companion.create

interface PolySymbolAccessModifier {

  val name: String

  companion object {

    operator fun get(name: String): PolySymbolAccessModifier =
      create(name)


    @JvmField
    val FILEPRIVATE: PolySymbolAccessModifier = create("fileprivate")

    @JvmField
    val INTERNAL: PolySymbolAccessModifier = create("internal")

    @JvmField
    val OPEN: PolySymbolAccessModifier = create("open")

    @JvmField
    val PACKAGE_PRIVATE: PolySymbolAccessModifier = create("package-private")

    @JvmField
    val PRIVATE: PolySymbolAccessModifier = create("private")

    @JvmField
    val PRIVATE_PACKAGE: PolySymbolAccessModifier = create("private[package]")

    @JvmField
    val PRIVATE_PROTECTED: PolySymbolAccessModifier = create("private protected")

    @JvmField
    val PRIVATE_THIS: PolySymbolAccessModifier = create("private[this]")

    @JvmField
    val PROTECTED: PolySymbolAccessModifier = create("protected")

    @JvmField
    val PROTECTED_INTERNAL: PolySymbolAccessModifier = create("protected internal")

    @JvmField
    val PROTECTED_PACKAGE: PolySymbolAccessModifier = create("protected[package]")

    @JvmField
    val PROTECTED_THIS: PolySymbolAccessModifier = create("protected[this]")

    @JvmField
    val PUB: PolySymbolAccessModifier = create("pub")

    @JvmField
    val PUB_CRATE: PolySymbolAccessModifier = create("pub(crate)")

    @JvmField
    val PUB_SUPER: PolySymbolAccessModifier = create("pub(super)")

    @JvmField
    val PUBLIC: PolySymbolAccessModifier = create("public")

  }
}