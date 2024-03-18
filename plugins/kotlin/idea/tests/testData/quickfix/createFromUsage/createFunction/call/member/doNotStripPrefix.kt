// "Create member function 'C.foo'" "true"
class Settings

fun isModified(settings: Settings, c: C) = c.<caret>foo(settings)

class C {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix