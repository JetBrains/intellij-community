// IS_APPLICABLE: false

fun <T> foo(b: (List<T>) -> Unit) {}

fun String.doSmth() {}

fun bar() {
    foo<<caret>String> {
        it[0].doSmth()
    }
}