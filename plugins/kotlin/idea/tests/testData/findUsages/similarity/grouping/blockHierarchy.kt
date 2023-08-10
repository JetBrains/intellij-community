val topLevelProperty = 1
val topLevelProperty2 = 1
fun Int.isI<caret>nt(): Boolean = true

fun check() {
    topLevelProperty.isInt()
    if (true) {
        topLevelProperty.isInt()
        topLevelProperty2.isInt()
        val topLevelProperty = 1
        topLevelProperty.isInt()
    } else {
        topLevelProperty.isInt()
        val topLevelProperty2 = 1
        topLevelProperty2.isInt()
    }

    topLevelProperty.isInt()
    topLevelProperty2.isInt()
    val topLevelProperty = 3
    topLevelProperty.isInt()

    fun t(topLevelProperty:  Int) {
        topLevelProperty.isInt()
        topLevelProperty2.isInt()
    }
}