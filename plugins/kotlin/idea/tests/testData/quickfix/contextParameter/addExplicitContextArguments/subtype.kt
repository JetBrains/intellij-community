// "Add explicit context argument" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1
// DISABLE_K2_ERRORS
open class Animal
class Dog : Animal()

context(a: Animal)
fun pet(): String = ""

fun main() {
    <caret>pet(Dog())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix