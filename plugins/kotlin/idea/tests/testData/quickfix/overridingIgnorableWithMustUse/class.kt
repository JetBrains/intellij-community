// "Add '@IgnorableReturnValue' annotation" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full

interface MyInterface {
    @IgnorableReturnValue
    fun foo(): String
}

@MustUseReturnValue
class MyClass : MyInterface {
    override fun <caret>foo(): String = ""
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix
