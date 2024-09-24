package main

import dependency.First
import dependency.Second

fun <T> test(a: T) where T : First, T : Second {
    a.anyExtension()
    a.firstExtension()
    a.secondExtension()
}<caret>