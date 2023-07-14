// "Create class 'Unknown'" "true"
// ACTION: Create class 'Unknown'
// ACTION: Create interface 'Unknown'
// ACTION: Create type parameter 'Unknown' in class 'A'
// DISABLE-ERRORS
class A : Unknown<caret> {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix