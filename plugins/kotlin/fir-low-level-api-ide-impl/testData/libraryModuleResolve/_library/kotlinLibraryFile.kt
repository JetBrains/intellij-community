package library

fun topLevelFunction() {}

fun topLevelFunction(s: String) {}

val topLevelProperty: Int = 0

class TopLevelClass() {

    constructor(s: String) : this() {}

    fun memberFunction() {}

    fun memberFunction(s: String) {}

    val memberProperty: Int = 0
}

enum class TopLevelEnum {
    ENTRY1,
    ENTRY2,
    ENTRY3,
}

object TopLevelObject {
    fun objectFunction() {}
}

class ClassWithImplicitConstructorAndCompanion {
    companion object {

    }
}

data class MyDataClass(val c: Int) {
    val a: String get() = ""
}
