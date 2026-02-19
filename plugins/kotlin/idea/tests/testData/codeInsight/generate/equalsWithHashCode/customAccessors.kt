class Test {
    val age by lazy { 15 + 10 }
    val color: String
        get() = "Purple"
    var serial: String = ""
        set(value) {
            field = value.toUpperCase()
        }
    var name: String = ""
    var id = 42
    <caret>
}