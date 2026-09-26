// "Remove 'inline' modifier" "false"
// ACTION: Expand boolean expression to 'if else'
// ACTION: Make 'bar' private
// ACTION: Make 'foo' public
// ACTION: Remove braces from all 'if' statements
// ERROR: Public-API inline function cannot access non-public-API 'private final val foo: Boolean defined in C'
// WITH_STDLIB
// K2_AFTER_ERROR: NON_PUBLIC_CALL_FROM_PUBLIC_INLINE
// K2_ERROR: NON_PUBLIC_CALL_FROM_PUBLIC_INLINE
class C {
    private val foo = true

    inline fun <reified T> bar(x: T): String {
        return if (foo<caret>) {
            T::class.java.toString()
        } else {
            ""
        }
    }
}