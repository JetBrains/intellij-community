// "Create function 'Foo'" "true"
// K2_AFTER_ERROR: Too many arguments for 'fun Foo(name: Boolean): Foo'.
// K2_AFTER_ERROR: Unresolved reference 'xxx'.
// IGNORE_K1
class Foo(
    val name: String,
) {
    companion object {
        fun of(number: Number): Foo {
            return Foo(<caret>number is xxx.bar())
        }
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction