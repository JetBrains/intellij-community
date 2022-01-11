// "Remove annotation" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class SomeOptInAnnotation

open class Base {
    open fun foo() {}
}

class Derived : Base() {
    <caret>@SomeOptInAnnotation
    override fun foo() {}
}
