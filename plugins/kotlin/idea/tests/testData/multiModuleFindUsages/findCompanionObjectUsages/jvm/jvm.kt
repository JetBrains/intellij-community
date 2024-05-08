actual class Clazz006 {
    actual companion object {
        actual val AA_A: Int
            get() = TODO("Not yet implemented")
    }
}

fun useCompanionInJvm() {
    println(Clazz006.AA_A)
}