// "Import extension function 'Rec.foo'" "true"
// ERROR: Too many arguments for public final fun foo(): Unit defined in kotlinpackage.one.Rec
package testingExtensionFunctionsImport

import kotlinpackage.one.Rec
import kotlinpackage.one.foo

fun some() {
    val r = Rec<String>()
    r.foo(<caret>"")
}

/* IGNORE_FIR */