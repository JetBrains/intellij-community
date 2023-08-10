// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(Int = ..., Double = ..., String = ...): Unit"
@file:JvmName("Foo")

@JvmOverloads
fun <caret>foo(
    x: Int = 0,
    y: Double = 0.0,
    z: String = "0"
) {

}

