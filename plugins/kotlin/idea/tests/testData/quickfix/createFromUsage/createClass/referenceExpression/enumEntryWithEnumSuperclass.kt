// "Create enum constant 'A'" "true"
package p

fun foo(): E = E.<caret>A

enum class E {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix