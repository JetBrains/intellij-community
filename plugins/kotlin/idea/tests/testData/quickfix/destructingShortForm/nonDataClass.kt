// "Convert to a full name-based destructuring form" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

class Foo(val a: String, val b: Int) {
    operator fun component1() = a
    operator fun component2() = b
}

fun bar(f: Foo) {
    val (<caret>b, a) = f
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix