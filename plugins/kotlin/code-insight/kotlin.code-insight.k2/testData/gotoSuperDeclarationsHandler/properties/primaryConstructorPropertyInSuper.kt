open class D(open val d: String = "") {}

class E : D() {
    override val <caret>d: String = ""
}