// INTENTION_TEXT: "Add parameter name to all callsites"

fun show(i: Int) {}
fun showAll(vararg i: Int) {}
val showF: (i: Int) -> Unit = {}

class Box {
    operator fun get(<caret>i: Int): Int = i
}

fun calls(box: Box) {
    show(box[0])
    showAll(box[0])
    showF(box[0])

    show(box.get(0))
}