// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

fun test() {
    ReallyLongClassN<caret>
}

// IGNORE_K1
// We are not checking the module because it could be either the one for the actual or the one for the expect declaration, but only one of them should be displayed.
// EXIST: {"lookupString":"ReallyLongClassName","tailText":" (a)","icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg","attributes":"","allLookupStrings":"ReallyLongClassName","itemText":"ReallyLongClassName"}
// EXIST: {"lookupString":"ReallyLongClassName2","tailText":" (a)","module":"jvm_JVM_1","icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg","attributes":"","allLookupStrings":"ReallyLongClassName2","itemText":"ReallyLongClassName2"}
// NOTHING_ELSE