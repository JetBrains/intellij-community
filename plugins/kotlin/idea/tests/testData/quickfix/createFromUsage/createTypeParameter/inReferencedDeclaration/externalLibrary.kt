// "Create type parameter in interface 'List'" "false"
// ACTION: Add full qualifier
// ACTION: Introduce import alias
// ERROR: One type argument expected for interface List<out E>
// WITH_STDLIB
fun foo(): List<String, String<caret>> {
    return listOf(1)
}