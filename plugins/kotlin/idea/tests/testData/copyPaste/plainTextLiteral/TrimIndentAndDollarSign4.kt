fun test(key: String, name: String) {
    """
        inline val $name: ResourceKey<String> = <caret>
    """.trimIndent()
}