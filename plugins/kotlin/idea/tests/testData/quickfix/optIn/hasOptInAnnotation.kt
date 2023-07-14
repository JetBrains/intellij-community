// "Opt in for 'B' on 'root'" "true"
// WITH_STDLIB
@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@A
fun f1() {}

@B
fun f2() {}

@OptIn(A::class)
fun root() {
    f1()
    <caret>f2()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixesFactory$HighPriorityUseOptInAnnotationFix