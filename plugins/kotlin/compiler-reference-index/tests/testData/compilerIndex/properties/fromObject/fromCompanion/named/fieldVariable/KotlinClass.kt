package one.two

class KotlinClass {
    companion object Named {
        @JvmField
        var field<caret>Variable = 42
    }
}
