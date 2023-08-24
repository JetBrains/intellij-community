// "Create class 'A'" "true"
package p

class T

fun foo(): T.<caret>A = throw Throwable("")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix