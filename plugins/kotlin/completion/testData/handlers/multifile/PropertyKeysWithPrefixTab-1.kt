import org.jetbrains.annotations.PropertyKey

fun message(@PropertyKey(resourceBundle = "TestBundle") key: String) = key

fun test() {
    message("foo.<caret>b")
}