// WITH_STDLIB
class Api(val name: String)

fun test(api: Api) {
    val name = <caret>if (api.name.isNotBlank())
        api.name
    else
        "John"
}