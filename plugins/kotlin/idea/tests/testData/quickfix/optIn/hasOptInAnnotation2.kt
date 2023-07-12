// "Opt in for 'A' on 'root'" "true"
// IGNORE_FIR
// WITH_STDLIB
@RequiresOptIn
annotation class A

@A
fun f1() {}

@OptIn()
fun root() {
    <caret>f1()
}