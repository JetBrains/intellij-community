// "Create class 'A'" "true"
package p

fun foo(): <caret>A<*, String> = throw Throwable("")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix