// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.polySymbols.impl.PolySymbolModifierData.Companion.create

interface PolySymbolModifier {

  val name: String

  companion object {

    operator fun get(name: String): PolySymbolModifier =
      create(name)

    @JvmField
    val ABSTRACT: PolySymbolModifier = create("abstract")

    @JvmField
    val ACCESSOR: PolySymbolModifier = create("accessor")

    @JvmField
    val ACTUAL: PolySymbolModifier = create("actual")

    @JvmField
    val ANNOTATION: PolySymbolModifier = create("annotation")

    @JvmField
    val ASYNC: PolySymbolModifier = create("async")

    @JvmField
    val ATOMIC: PolySymbolModifier = create("atomic")

    @JvmField
    val CLASS: PolySymbolModifier = create("class")

    @JvmField
    val COMPANION: PolySymbolModifier = create("companion")

    @JvmField
    val CONST: PolySymbolModifier = create("const")

    @JvmField
    val CONSTRUCTOR: PolySymbolModifier = create("constructor")

    @JvmField
    val CONSTEXPR: PolySymbolModifier = create("constexpr")

    @JvmField
    val CONVENIENCE: PolySymbolModifier = create("convenience")

    @JvmField
    val CROSSINLINE: PolySymbolModifier = create("crossinline")

    @JvmField
    val DATA: PolySymbolModifier = create("data")

    @JvmField
    val DECLARE: PolySymbolModifier = create("declare")

    @JvmField
    val DEFAULT: PolySymbolModifier = create("default")

    @JvmField
    val DEFER: PolySymbolModifier = create("defer")

    @JvmField
    val DYNAMIC: PolySymbolModifier = create("dynamic")

    @JvmField
    val ENUM: PolySymbolModifier = create("enum")

    @JvmField
    val EVENT: PolySymbolModifier = create("event")

    @JvmField
    val EXPECT: PolySymbolModifier = create("expect")

    @JvmField
    val EXPLICIT: PolySymbolModifier = create("explicit")

    @JvmField
    val EXPORT: PolySymbolModifier = create("export")

    @JvmField
    val EXTERN: PolySymbolModifier = create("extern")

    @JvmField
    val EXTERNAL: PolySymbolModifier = create("external")

    @JvmField
    val FINAL: PolySymbolModifier = create("final")

    @JvmField
    val FRIEND: PolySymbolModifier = create("friend")

    @JvmField
    val GENERATOR: PolySymbolModifier = create("generator")

    @JvmField
    val GET: PolySymbolModifier = create("get")

    @JvmField
    val IN: PolySymbolModifier = create("in")

    @JvmField
    val INDIRECT: PolySymbolModifier = create("indirect")

    @JvmField
    val INFIX: PolySymbolModifier = create("infix")

    @JvmField
    val INNER: PolySymbolModifier = create("inner")

    @JvmField
    val INLINE: PolySymbolModifier = create("inline")

    @JvmField
    val LATEINIT: PolySymbolModifier = create("lateinit")

    @JvmField
    val LAZY: PolySymbolModifier = create("lazy")

    @JvmField
    val MUTABLE: PolySymbolModifier = create("mutable")

    @JvmField
    val MUTATING: PolySymbolModifier = create("mutating")

    @JvmField
    val NATIVE: PolySymbolModifier = create("native")

    @JvmField
    val NOEXCEPT: PolySymbolModifier = create("noexcept")

    @JvmField
    val NOINLINE: PolySymbolModifier = create("noinline")

    @JvmField
    val NONMUTATING: PolySymbolModifier = create("nonmutating")

    @JvmField
    val OPEN: PolySymbolModifier = create("open")

    @JvmField
    val OPERATOR: PolySymbolModifier = create("operator")

    @JvmField
    val OPTIONAL: PolySymbolModifier = create("optional")

    @JvmField
    val OVERRIDE: PolySymbolModifier = create("override")

    @JvmField
    val OUT: PolySymbolModifier = create("out")

    @JvmField
    val PARTIAL: PolySymbolModifier = create("partial")

    @JvmField
    val PROTO: PolySymbolModifier = create("proto")

    @JvmField
    val REIFIED: PolySymbolModifier = create("reified")

    @JvmField
    val READONLY: PolySymbolModifier = create("readonly")

    @JvmField
    val REGISTER: PolySymbolModifier = create("register")

    @JvmField
    val REQUIRED: PolySymbolModifier = create("required")

    @JvmField
    val SEALED: PolySymbolModifier = create("sealed")

    @JvmField
    val SET: PolySymbolModifier = create("set")

    @JvmField
    val SYNCHRONIZED: PolySymbolModifier = create("synchronized")

    @JvmField
    val STATIC: PolySymbolModifier = create("static")

    @JvmField
    val STRICTFP: PolySymbolModifier = create("strictfp")

    @JvmField
    val SUSPEND: PolySymbolModifier = create("suspend")

    @JvmField
    val TAILREC: PolySymbolModifier = create("tailrec")

    @JvmField
    val THREAD_LOCAL: PolySymbolModifier = create("thread_local")

    @JvmField
    val TRANSIENT: PolySymbolModifier = create("transient")

    @JvmField
    val UNSAFE: PolySymbolModifier = create("unsafe")

    @JvmField
    val VALUE: PolySymbolModifier = create("value")

    @JvmField
    val VARARG: PolySymbolModifier = create("vararg")

    @JvmField
    val VIRTUAL: PolySymbolModifier = create("virtual")

    @JvmField
    val VOLATILE: PolySymbolModifier = create("volatile")

  }

}