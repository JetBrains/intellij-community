// "Create class 'Foo'" "true"
// DISABLE-ERRORS
// IGNORE_K2
open class B

class A<T>(val t: T) {
    var x: B by <caret>Foo(t, "")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction