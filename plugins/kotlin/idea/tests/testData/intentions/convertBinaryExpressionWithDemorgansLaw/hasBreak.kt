// IS_APPLICABLE: false
// WITH_STDLIB
fun test(xs: List<String>) {
    for (x in xs) {
        x.isNotEmpty() ||<caret> (break)
    }
}