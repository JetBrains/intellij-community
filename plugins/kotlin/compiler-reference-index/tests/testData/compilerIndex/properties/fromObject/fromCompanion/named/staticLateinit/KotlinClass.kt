package one.two

class KotlinClass {
    companion object Named {
        @JvmStatic
        lateinit var staticLateinit<caret>: KotlinClass
    }
}
