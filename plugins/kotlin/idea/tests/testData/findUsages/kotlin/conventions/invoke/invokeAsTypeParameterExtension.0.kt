// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun <T> T.invoke(): T"

interface Foo {
    companion object : Friend
}

interface Friend

operator fun <T> T.in<caret>voke(): T = this

fun test() {
    Foo()
}


// IGNORE_K2_LOG