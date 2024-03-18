// "Create class 'Unknown'" "true"
// DISABLE-ERRORS
class A() : Unknown<caret> {
    constructor(i: Int) : this()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix