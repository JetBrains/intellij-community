// PROBLEM: none
class City(
    population: Int,
    val <caret>GDP: Long
) {
    val GDPPerPerson: Double = if (population != 0) GDP.toDouble() / population else 0.0
}