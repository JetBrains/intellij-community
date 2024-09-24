package main

fun <T> test(a: T) {
    a.extension<caret>
}

// EXIST: anyExtension
