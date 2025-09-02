fun <T> Iterable<T>.eachIndexed(name: String, action: (Int, T) -> Unit) { }

fun context(i: Iterable<String>) {
    i.eachIndexed("") {} { <caret> }
}
// IGNORE_K1
// Text: (name: String, <highlight>action: (Int, String) -> Unit</highlight>), Disabled: false, Strikeout: false, Green: true