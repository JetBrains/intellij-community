// "Create object 'Foo'" "true"
// DISABLE_ERRORS
// IGNORE_K2
open class B

class A {
    var x: B by <caret>Foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction