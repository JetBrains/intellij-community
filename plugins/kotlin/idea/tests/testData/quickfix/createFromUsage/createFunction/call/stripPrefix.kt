// "Create member function 'C.foo'" "true"
class SetOptions

fun isModified(setOptions: SetOptions, c: C) = c.<caret>foo(setOptions)

class C {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix