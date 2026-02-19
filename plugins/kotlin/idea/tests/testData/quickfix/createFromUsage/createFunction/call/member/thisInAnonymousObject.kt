// "Create member function 'bar'" "true"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

interface A
interface B

fun main() {
    object : Object(), A, B {
        fun foo() {
            this.b<caret>ar()
        }
    }
}

// IGNORE_K1