// "Remove 'inline' modifier" "false"
// ACTION: Expand boolean expression to 'if else'
// ACTION: Make 'bar' protected
// ACTION: Make 'protectedMethod' public
// ACTION: Remove braces from all 'if' statements
// ACTION: Replace with generated @PublishedApi bridge call '`access$protectedMethod`(...)'
// ERROR: Protected function call from public-API inline function is prohibited
// WITH_STDLIB
// K2_AFTER_ERROR: PROTECTED_CALL_FROM_PUBLIC_INLINE_ERROR
// K2_ERROR: PROTECTED_CALL_FROM_PUBLIC_INLINE_ERROR
open class Foo {
    protected fun protectedMethod() = true

    inline fun <reified T> bar(x: T): String {
        return if (<caret>protectedMethod()) {
            T::class.java.toString()
        } else {
            ""
        }
    }
}