package main

import dependency.Base
import dependency.Extra

fun test(a: Base) {
    if (a is Extra) {
        // a: Base & Extra

        a.anyExtension()
        a.baseExtension()
        a.extraExtension()
    }
}<caret>