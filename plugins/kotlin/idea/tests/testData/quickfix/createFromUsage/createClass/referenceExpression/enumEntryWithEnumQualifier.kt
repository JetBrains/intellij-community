// "Create enum constant 'A'" "true"
package p

fun foo() = X.<caret>A

enum class X {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix