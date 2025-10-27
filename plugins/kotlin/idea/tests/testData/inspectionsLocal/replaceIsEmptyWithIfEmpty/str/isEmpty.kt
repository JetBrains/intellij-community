// WITH_STDLIB
class Api(val name: String)

fun test(api: Api) {
    val name = <caret>if (api.name.isEmpty()) "John" else api.name
}