
// "Make 'obj' 'private'" "true"

open class A {
    val <caret>obj = object : A() {}
    val obj2 = listOf(obj)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix