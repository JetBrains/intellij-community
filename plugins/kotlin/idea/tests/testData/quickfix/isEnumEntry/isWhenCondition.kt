// "Remove 'is'" "true"
enum class Foo { A }

fun test(foo: Foo): Int = when (foo) {
    is <caret>Foo.A -> 1
    else -> 2
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveIsFromIsEnumEntryFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveIsFromIsEnumEntryFix