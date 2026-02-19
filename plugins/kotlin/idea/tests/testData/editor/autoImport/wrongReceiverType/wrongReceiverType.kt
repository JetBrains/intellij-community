package main

import dependency.FromDependency

class UnrelatedClass

fun UnrelatedClass.extension()

fun test(f: FromDependency) {
    f.extension() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
}