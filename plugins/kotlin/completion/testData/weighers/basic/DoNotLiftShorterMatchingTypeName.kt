// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_COMPARISON
// FIR_IDENTICAL

interface Apple
class RedApple : Apple
class YellowApple : Apple
class GreenApple : Apple
class App

fun juice(apple: <caret>)

// ORDER: Apple
// ORDER: GreenApple
// ORDER: RedApple
// ORDER: YellowApple
// IGNORE_K1