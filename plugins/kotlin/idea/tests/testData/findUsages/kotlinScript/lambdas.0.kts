// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// GROUPING_RULES: org.jetbrains.kotlin.idea.base.searching.usages.KotlinDeclarationGroupingRule
// OPTIONS: usages, constructorUsages
class <caret>Foo

fun someThing(name: String, block: () -> Unit) { block() }

someThing("x") {
    val foo = Foo()
}
