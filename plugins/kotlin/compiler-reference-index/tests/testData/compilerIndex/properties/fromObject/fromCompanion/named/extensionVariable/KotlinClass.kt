package one.two

class KotlinClass {
    companion object Named {
        var Int.extension<caret>Variable
            get() = 42
            set(value) {}
    }
}
