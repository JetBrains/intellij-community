// "Remove 'inline' modifier" "false"
// ACTION: Expand boolean expression to 'if else'
// ACTION: Make 'bar' private
// ACTION: Make 'foo' public
// ACTION: Remove braces from all 'if' statements
// ERROR: Public-API inline function cannot access non-public-API 'private final val foo: Boolean defined in C'
// K2_AFTER_ERROR: Public-API inline function cannot access non-public-API property.
// WITH_STDLIB
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