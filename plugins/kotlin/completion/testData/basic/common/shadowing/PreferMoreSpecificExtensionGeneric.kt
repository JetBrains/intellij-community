// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun <T> List<T>.xxx(){}
fun <T> Iterable<T>.xxx(){}

fun foo() {
    listOf(1).xx<caret>
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for List<T> in <root>", typeText: "Unit", icon: "Function"}
// NOTHING_ELSE
