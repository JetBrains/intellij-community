package test

import dependency.invoke

fun test(action: () -> Unit) {
    action()
}