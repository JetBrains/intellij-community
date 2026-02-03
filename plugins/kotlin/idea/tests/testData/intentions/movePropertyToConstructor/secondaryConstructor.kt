// IS_APPLICABLE: false

class SimpleWConstructor() {
    val b<caret>: String = ""
    constructor(s: String): this()
    constructor(i: Int): this()
}