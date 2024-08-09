// "Replace with generated @PublishedApi bridge call '`access$test`(...)'" "true"
annotation class Z

open class ABase<T> {
    @Z
    protected fun test(p: T) {
    }

    fun param(): T {
        return null!!
    }

    inline fun test() {
        {
            <caret>test(param())
        }()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix