// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class SomeCompletionClass(a: Int)

fun create(): SomeCompletionClas<caret> {

}


// EXIST: {"lookupString": "SomeCompletionClass", "tailText": " (<root>)" }
// NOTHING_ELSE