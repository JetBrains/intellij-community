// PROBLEM: none
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
// K2_ERROR: UNSAFE_CALL
fun main(args: Array<String>) {
    val foo: String? = "foo"
    if<caret> {
        foo.length
    } else null
}
