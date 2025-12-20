// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class SomePrefixANotException
class SomePrefixBException: Exception()
class SomePrefixCNotException

fun test(
    SomePrefixANotExceptionValue: Int,
    SomePrefixBExceptionValue: Throwable,
    SomePrefixCNotExceptionValue: Int,
) {
    throw SomePrefix<caret>
}

// WITH_ORDER
// EXIST: SomePrefixBExceptionValue
// EXIST: { itemText: "SomePrefixBException", tailText: "() (<root>)" }
// EXIST: { itemText: "SomePrefixBException", tailText: " (<root>)" }
// EXIST: SomePrefixANotException
// EXIST: SomePrefixCNotException
// EXIST: SomePrefixANotExceptionValue
// EXIST: SomePrefixCNotExceptionValue

// IGNORE_K1