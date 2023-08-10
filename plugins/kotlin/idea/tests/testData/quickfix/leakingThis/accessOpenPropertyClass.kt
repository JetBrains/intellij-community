// "Make 'My' 'final'" "true"

open class My(open val x: Int) {
    val y = <caret>x
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10