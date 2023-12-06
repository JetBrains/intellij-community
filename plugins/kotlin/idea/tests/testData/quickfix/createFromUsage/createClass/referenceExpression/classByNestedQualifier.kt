// "Create class 'B'" "true"
// ERROR: Unresolved reference: C
package p

fun foo() = A.<caret>B.C

class A {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix