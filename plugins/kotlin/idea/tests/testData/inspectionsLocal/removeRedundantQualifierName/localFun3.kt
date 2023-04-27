package my.simple.name

fun say() {}

class Inner {
    fun a() {
        Inner<caret>.say()
    }

    companion object {
        fun say() {}
    }
}
