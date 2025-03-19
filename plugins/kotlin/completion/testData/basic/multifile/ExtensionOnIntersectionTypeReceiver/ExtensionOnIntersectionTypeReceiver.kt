package main

import dependency.Base
import dependency.Extra

fun test(a: Base) {
    if (a is Extra) {
        // a: Base & Extra

        a.extension<caret>
    }
}

// EXIST: anyExtension
// EXIST: baseExtension
// EXIST: extraExtension
