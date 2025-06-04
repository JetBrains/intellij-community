// WITH_STDLIB
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package otherPackage

@DslMarker
annotation class HtmlDsl

@DslMarker
annotation class HtmlDsl2

class Tag(val name: String) {
    private val children = mutableListOf<Tag>()
    private val attributes = mutableMapOf<String, String>()

    @HtmlDsl
    fun tag(name: String, init: Tag.() -> Unit) {
        val child = Tag(name)
        child.init()
        children.add(child)
    }

    @HtmlDsl2
    fun attr(name: String, value: String) {
        attributes[name] = value
    }
}

@HtmlDsl
fun html(init: Tag.() -> Unit): Tag {
    val tag = Tag("html")
    tag.init()
    return tag
}

fun testHtml() {
    html { // Must be highlighted with a color corresponding to the DSL
        tag("div") {  // Must be highlighted with a color corresponding to the DSL, same as `html`
            attr("class", "container")  // Must be highlighted with a color corresponding to the DSL
        }
    }
}