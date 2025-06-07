// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

fun test() {
    reallyLongFunNam<caret>
}

// IGNORE_K1
// We are not checking the module because it could be either the one for the actual or the one for the expect declaration, but only one of them should be displayed.
// EXIST: {"lookupString":"reallyLongFunName","tailText":"() (a)","typeText":"Unit","icon":"Function","attributes":"","allLookupStrings":"reallyLongFunName","itemText":"reallyLongFunName"}
// EXIST: {"lookupString":"reallyLongFunName2","tailText":"() (a)","typeText":"Unit","module":"jvm_JVM_1","icon":"Function","attributes":"","allLookupStrings":"reallyLongFunName2","itemText":"reallyLongFunName2"}
// NOTHING_ELSE