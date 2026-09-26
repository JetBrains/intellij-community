package main

import dependency.First
import dependency.Second

fun <T> test(a: T) where T : First, T : Second {
    a.extension<caret>
}

// EXIST: anyExtension
// EXIST: firstExtension
// EXIST: secondExtension
