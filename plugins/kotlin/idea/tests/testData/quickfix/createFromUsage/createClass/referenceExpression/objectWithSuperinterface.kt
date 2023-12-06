// "Create object 'A'" "true"
package p

fun foo(): X = <caret>A

interface X {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix