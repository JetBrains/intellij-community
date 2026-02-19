// language=kotlin
val x = """
    class Foo {
        val x = 42 
        fun <T> i<caret>d(x: T) = x 
    }
""".trimIndent()