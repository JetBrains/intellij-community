// "Create extension function 'A.bar'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

interface A
interface B

fun main() {
    object : A, B {
        fun foo() {
            this.b<caret>ar()
        }
    }
}
