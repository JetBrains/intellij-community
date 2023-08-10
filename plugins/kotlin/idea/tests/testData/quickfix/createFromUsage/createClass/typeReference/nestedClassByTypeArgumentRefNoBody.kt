// "Create class 'X'" "true"
open class A<T>

class B : A<B.<caret>X>()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix