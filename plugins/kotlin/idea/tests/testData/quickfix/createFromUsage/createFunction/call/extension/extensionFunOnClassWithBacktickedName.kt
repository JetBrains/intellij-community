// "Create extension function '`Foo-Bar`.Companion.test'" "true"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: UNRESOLVED_REFERENCE

class `Foo-Bar`

fun main() {
    `Foo-Bar`.<caret>test()
}
