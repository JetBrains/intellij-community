// "Create extension function 'Any.get'" "true"
// WITH_STDLIB

fun x (y: Any) {
    val z: Any = y<caret>[""]
}
