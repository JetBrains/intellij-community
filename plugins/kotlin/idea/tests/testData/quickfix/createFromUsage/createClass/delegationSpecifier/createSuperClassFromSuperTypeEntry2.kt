// "Create class 'Unknown'" "true"
// DISABLE-ERRORS
class A : Unknown<caret> {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix