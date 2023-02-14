// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// ERROR: Incompatible types: Friend and Foo.Companion
// CRI_IGNORE

interface Foo {
    companion object
}

interface Friend

operator fun <T> T.inv<caret>oke(): T = this

fun test() {
    if (Foo.Companion is Friend) {
        Foo.Companion()
    }
}


// IGNORE_FIR_LOG