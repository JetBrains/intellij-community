// "Replace with generated @PublishedApi bridge call '`access$test`(...)'" "true"
annotation class Z

open class ABase {
    @Z
    protected fun String.test(p: Int) {
    }


    inline fun test() {
        {
            "123".<caret>test(1)
        }()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix