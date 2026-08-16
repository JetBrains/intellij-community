// FIX: Replace with 'forEachIndexed'
// PRIORITY: NORMAL

fun test(list: List<String>) {
    list.map<caret>Indexed { index, string ->
        println("$index: $string")
    }
}
