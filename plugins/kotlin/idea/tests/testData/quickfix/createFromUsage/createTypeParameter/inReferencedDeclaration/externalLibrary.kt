// "Create type parameter in interface 'List'" "false"
// ERROR: One type argument expected for interface List<out E>
// WITH_STDLIB
fun foo(): List<String, String<caret>> {
    return listOf(1)
}