// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun <T> T.invoke(): T"
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


// IGNORE_K2_LOG