package fixtures

fun main() {
    val a: Animal = Dog()
    println(a.speak())
    val b: Animal = Cat()
    println(b.speak())
}
