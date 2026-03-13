// "Add secondary constructor to 'Foo'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Boolean', but 'String' was expected.
// K2_ERROR: Too many arguments for 'constructor(name: String): Foo'.
// K2_ERROR: Unresolved reference 'xxx'.
// IGNORE_K1
// K2_AFTER_ERROR: None of the following candidates is applicable:<br><br>constructor(name: String): Foo:<br>  Too many arguments for 'constructor(name: String): Foo'.<br>  Argument type mismatch: actual type is 'Boolean', but 'String' was expected.<br><br>constructor(name: Boolean): Foo:<br>  Too many arguments for 'constructor(name: Boolean): Foo'.
// K2_AFTER_ERROR: There's a cycle in the delegation calls chain.
// K2_AFTER_ERROR: Unresolved reference 'xxx'.
class Foo(
    val name: String,
) {
    companion object {
        fun of(number: Number): Foo {
            return Foo(<caret>number is xxx.bar())
        }
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.AddConstructorFix