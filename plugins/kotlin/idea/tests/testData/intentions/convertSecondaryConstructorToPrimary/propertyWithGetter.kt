// AFTER-WARNING: Parameter 's' is never used
fun log(s: String) {
}

class A {
    var x: String
        get() {
            log(field)
            return field
        }

    <caret>constructor(x: String) {
        this.x = x
    }
}
