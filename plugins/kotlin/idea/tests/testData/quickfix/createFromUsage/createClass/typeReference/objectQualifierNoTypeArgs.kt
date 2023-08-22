// "Create object 'A'" "true"
// ERROR: Unresolved reference: B
package p

fun foo(): <caret>A.B = throw Throwable("")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix