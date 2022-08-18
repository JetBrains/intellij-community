// WITH_STDLIB
fun foo(args: Array<String>, b: Boolean) {
    !(args.isNotEmpty() &&<caret> !b)
}
