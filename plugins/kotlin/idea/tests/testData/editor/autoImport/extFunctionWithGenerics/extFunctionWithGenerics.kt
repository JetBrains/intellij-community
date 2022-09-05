package testingExtensionFunctionsImport

import pkg.Rec

fun some() {
    val r = Rec<String>()
    r.foo("")
}<caret>

/* IGNORE_FIR */