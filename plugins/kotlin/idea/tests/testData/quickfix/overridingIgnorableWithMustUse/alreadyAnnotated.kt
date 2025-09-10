// "Add '@IgnorableReturnValue' annotation" "false"
// WITH_STDLIB
// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full

@MustUseReturnValue
interface MyInterface {
    @IgnorableReturnValue fun foo(): String
}

@MustUseReturnValue
class MyClass : MyInterface {
    @IgnorableReturnValue
    override fun <caret>foo(): String = ""
}
