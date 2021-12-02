// "Opt in for 'A' in 'root'" "true"
// WITH_STDLIB
@RequiresOptIn
annotation class A

@A
fun f1() {}

@OptIn
fun root() {
    <caret>f1()
}