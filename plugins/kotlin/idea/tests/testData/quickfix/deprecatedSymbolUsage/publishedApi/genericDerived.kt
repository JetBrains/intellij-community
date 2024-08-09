// "Replace with generated @PublishedApi bridge call '`access$test`(...)'" "true"
open class ABase<T> {
    protected fun test(p: T) {
    }

    fun param(): T {
        return null!!
    }

}

open class A : ABase<String>() {

    inline fun test() {
        {
            <caret>test(param())
        }()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix