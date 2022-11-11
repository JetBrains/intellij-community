package one.two

class KotlinClass {
    companion object Named {
        @JvmStatic
        var static<caret>Variable = 4
    }
}
