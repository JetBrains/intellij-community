// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun foo(@Suppress("UNCHECKED_CAST") p: () -> Unit){}

fun bar() {
    <caret>
}

// EXIST: { lookupString:"foo", itemText: "foo", tailText: " {...} (p: () -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
