// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun test(): JavaConstructor {
    return JavaConstructo<caret>
}


// EXIST: {"lookupString": "JavaConstructor", "tailText": " (<root>)" }
// EXIST: {"lookupString": "JavaConstructor", "tailText": "(s: String!) (<root>)" }
// NOTHING_ELSE