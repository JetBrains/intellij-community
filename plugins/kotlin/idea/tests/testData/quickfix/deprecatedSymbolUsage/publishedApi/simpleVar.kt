// "Replace with generated @PublishedApi bridge call '`access$prop`'" "true"
annotation class Z

open class ABase {
    @Z
    protected var prop = 1

    inline fun test() {
        {
            <caret>prop
        }()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix