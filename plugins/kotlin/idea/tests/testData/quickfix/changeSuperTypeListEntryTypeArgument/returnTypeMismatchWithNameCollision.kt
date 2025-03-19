// "Change type argument to String" "true"

class String

interface Foo<T> { fun foo(): T}

class FooImpl : Foo<Int> {
    override fun foo(): <caret>kotlin.String = ""
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeSuperTypeListEntryTypeArgumentFixFactory$ChangeSuperTypeListEntryTypeArgumentFix