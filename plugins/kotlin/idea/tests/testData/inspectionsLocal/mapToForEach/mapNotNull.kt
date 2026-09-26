// FIX: Replace with 'forEach'

fun test(list: List<String>) {
    list.mapNot<caret>Null { string ->
        println(string)
    }
}
