// "Create abstract function 'A.bar'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// ERROR: Object is not abstract and does not implement abstract member public abstract fun bar(): Unit defined in A
// K2_AFTER_ERROR: Class '<anonymous>' is not abstract and does not implement abstract member:<br>fun bar(): Unit

interface A
interface B

fun main() {
    object : Object(), A, B {
        fun foo() {
            this.b<caret>ar()
        }
    }
}
