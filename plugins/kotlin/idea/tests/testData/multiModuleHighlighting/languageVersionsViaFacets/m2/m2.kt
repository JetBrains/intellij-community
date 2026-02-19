package languageVersion1_0

public fun useJavaMap1_0(): java.util.HashMap<Int, Int> {
    val g = java.util.HashMap<Int, Int>()
    g.values.removeIf { it < 5 }
    return g
}

val use1_1 = languageVersion1_1.useJavaMap1_1().values.removeIf { it < 5 }
