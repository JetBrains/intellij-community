// "Add '@IgnorableReturnValue' annotation" "false"
// WITH_STDLIB
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full

@MustUseReturnValues
interface MyInterface {
    @IgnorableReturnValue fun foo(): String
}

@MustUseReturnValues
class MyClass : MyInterface {
    @IgnorableReturnValue
    override fun <caret>foo(): String = ""
}
