// COMPILER_ARGUMENTS: -XXLanguage:+RangeUntilOperator
// WITH_STDLIB
// LANGUAGE_VERSION: 1.9
package kotlin

class MyObj {
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    @SinceKotlin("1.9")
    @WasExperimental(ExperimentalStdlibApi::class)
    @kotlin.internal.InlineOnly
    operator inline fun rangeUntil(o: MyObj) = Unit
}

// Since `until` is only defined for primitives, we create this fake function in kotlin package
// to activate the inspection and make it look at MyObj.rangeUntil, which is without opt-in.
infix fun MyObj.until(o: MyObj) = Unit

fun main() {
    MyObj() un<caret>til MyObj()
}