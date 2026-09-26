// FIX: Use property access syntax
fun main() {
    val w = ClassInheritingBoth()
    w.<caret>getName()
}

class ClassInheritingBoth: JavaName, KotlinName {

    override fun getName(): String {
        return "String"
    }
}

interface KotlinName {
    fun getName(): String
}