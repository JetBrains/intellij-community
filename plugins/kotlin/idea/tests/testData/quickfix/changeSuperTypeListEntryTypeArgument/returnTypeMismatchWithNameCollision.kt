// "Change type argument to String" "true"
// K2_ERROR: Return type of 'fun foo(): String' is not a subtype of the return type of the overridden member 'fun foo(): Int' defined in 'FooImpl'.

class String

interface Foo<T> { fun foo(): T}

class FooImpl : Foo<Int> {
    override fun foo(): <caret>kotlin.String = ""
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeSuperTypeListEntryTypeArgumentFixFactory$ChangeSuperTypeListEntryTypeArgumentFix