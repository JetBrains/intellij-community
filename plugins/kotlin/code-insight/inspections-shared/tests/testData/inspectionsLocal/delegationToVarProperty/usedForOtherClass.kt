// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// PROBLEM: none
class Foo(<caret>var text: CharSequence) {
    inner class Bar: CharSequence by text
}