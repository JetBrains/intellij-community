// "Replace with generated @PublishedApi bridge call '`access$test`(...)'" "true"
// K2_ERROR: Protected function call from public-API inline function is prohibited.
annotation class Z

open class ABase {
    @Z
    protected fun test(p: Int) {
    }


    inline fun test() {
        {
            <caret>test(1)
        }()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix