expect class Clazz006 {
    companion object {
        val AA_A: Int
    }
}

fun useCompanionInCommon() {
    println(Clazz006.AA_A)
}