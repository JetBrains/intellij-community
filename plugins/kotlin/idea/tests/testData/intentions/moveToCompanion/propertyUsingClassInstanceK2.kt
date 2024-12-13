// SHOULD_FAIL_WITH: Usages of outer class instance inside declaration 'y' won't be processed
// IGNORE_K1
class A {
    val x = 1
    val <caret>y = x + 1
}