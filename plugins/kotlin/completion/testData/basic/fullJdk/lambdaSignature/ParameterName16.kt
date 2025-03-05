// IGNORE_K1

interface MapEntry<out K, out V> {

    val key: K

    val value: V
}

operator fun <K, V> MapEntry<K, V>.component1(): K = key

operator fun <K, V> MapEntry<K, V>.component2(): V = value

interface MutableMapEntry<K, V> : MapEntry<K, V> {

    override var value: V
}

fun bar() {
    class MutableMapEntryImpl : MutableMapEntry<Int, String> {

        override val key: Int
            get() = 42

        override var value: String
            get() = ""
            set(value) {}
    }

    MutableMapEntryImpl().let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "entry", tailText: " -> ", allLookupStrings: "entry", typeText: "MutableMapEntryImpl" }
// EXIST: { itemText: "mapEntry", tailText: " -> ", allLookupStrings: "mapEntry", typeText: "MutableMapEntryImpl" }
// EXIST: { itemText: "mutableMapEntry", tailText: " -> ", allLookupStrings: "mutableMapEntry", typeText: "MutableMapEntryImpl" }
// EXIST: { lookupString: "key", itemText: "(key, value)", tailText: " -> ", allLookupStrings: "key, value", typeText: "(Int, String)" }