// "Remove 'inline' modifier" "false"
// ACTION: Make 'foo' public
// ACTION: Make 'bar' private
// ACTION: Expand boolean expression to 'if else'
// ACTION: Remove braces from all 'if' statements
// ERROR: Public-API inline function cannot access non-public-API 'private final val foo: Boolean defined in C'
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