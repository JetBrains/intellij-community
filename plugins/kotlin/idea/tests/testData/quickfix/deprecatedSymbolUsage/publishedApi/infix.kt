// "Replace with generated @PublishedApi bridge call '`access$foo`(...)'" "true"
open class A {
    protected infix fun foo(p: Int) {
    }

    inline fun call() {
        A() foo<caret> 8
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix