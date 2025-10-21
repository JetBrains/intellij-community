// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlin.collections.listOf as list

fun foo() {
    lis<caret>
}

// EXIST: { lookupString: "list", itemText: "list", tailText: "() (kotlin.collections.listOf)", icon: "Function"}
