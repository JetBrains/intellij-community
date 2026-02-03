fun <T> Iterable<T>.eachIndexed(name: String, action: (Int, T) -> Unit) { }

fun context(i: Iterable<String>) {
    i.eachIndexed("") {} { <caret> }
}
