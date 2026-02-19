// WITH_STDLIB
class Api(val name: String)

fun test(api: Api) {
    val name = <caret>if (api.name.isBlank()) "John" else api.name
}