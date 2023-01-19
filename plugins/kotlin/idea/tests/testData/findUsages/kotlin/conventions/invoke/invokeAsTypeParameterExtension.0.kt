// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

interface Foo {
    companion object : Friend
}

interface Friend

operator fun <T> T.in<caret>voke(): T = this

fun test() {
    Foo()
}

// FIR_COMPARISON
// IGNORE_FIR_LOG