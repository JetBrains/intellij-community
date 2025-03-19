package test

import test.WithCompanion.Companion.invoke

interface WithCompanion {
    companion object {
        operator fun invoke() {}
    }
}

fun usage() {
    WithCompanion() // invoke call, no need for import
}