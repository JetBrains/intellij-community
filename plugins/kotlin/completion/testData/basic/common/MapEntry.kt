// FIR_IDENTICAL

class StringEntry(
    override val key: String,
    override val value: String,
) : Map.<caret>Entry<String, String>

// EXIST: { itemText: "Entry", lookupString: "Entry", tailText: "<K, V> (kotlin.collections.Map)", icon: "org/jetbrains/kotlin/idea/icons/interfaceKotlin.svg", attributes: "" }