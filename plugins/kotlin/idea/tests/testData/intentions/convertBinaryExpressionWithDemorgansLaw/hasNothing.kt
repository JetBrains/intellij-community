// IS_APPLICABLE: false
// WITH_STDLIB
fun main(args: Array<String>) {
    args.isNotEmpty() ||<caret> foo()
}

fun foo(): Nothing = throw RuntimeException()