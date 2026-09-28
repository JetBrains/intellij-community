// "Add return keyword" "true"
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
open class Animal
class Cat : Animal()

fun cat(): Cat = Cat()

fun test(condition: Boolean): Animal {
    if (condition) {
        <caret>cat()
    }
    return Animal()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.NoReturnValueFactory$AddReturnKeywordFix
