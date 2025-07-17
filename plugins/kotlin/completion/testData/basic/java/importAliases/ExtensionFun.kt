// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlin.collections.firstOrNull as aaa

fun foo() {
    listOf(1, 2).aa<caret>
}

// EXIST: { lookupString: "aaa", itemText: "aaa", tailText: "() for List<T> (kotlin.collections.firstOrNull)", icon: "Function"}
