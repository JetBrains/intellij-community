fun test() {
    buildMap {
        buildMap<Int, String> {
            buildMap<String, Int> {
                put<caret>
            }
        }
    }
}

// EXIST: {"lookupString":"put","tailText":"(key: K, value: V)","typeText":"V?","icon":"nodes/abstractMethod.svg","attributes":"grayed","allLookupStrings":"put","itemText":"put"}
// EXIST: {"lookupString":"putAll","tailText":"(pairs: Iterable<Pair<K, V>>) for MutableMap<in K, in V> in kotlin.collections","typeText":"Unit","icon":"nodes/function.svg","attributes":"grayed","allLookupStrings":"putAll","itemText":"putAll"}
// EXIST: {"lookupString":"putAll","tailText":"(pairs: Sequence<Pair<K, V>>) for MutableMap<in K, in V> in kotlin.collections","typeText":"Unit","icon":"nodes/function.svg","attributes":"grayed","allLookupStrings":"putAll","itemText":"putAll"}
// EXIST: {"lookupString":"putAll","tailText":"(pairs: Array<out Pair<K, V>>) for MutableMap<in K, in V> in kotlin.collections","typeText":"Unit","icon":"nodes/function.svg","attributes":"grayed","allLookupStrings":"putAll","itemText":"putAll"}
// EXIST: {"lookupString":"putAll","tailText":"(from: Map<out K, V>)","typeText":"Unit","icon":"nodes/abstractMethod.svg","attributes":"grayed","allLookupStrings":"putAll","itemText":"putAll"}
// EXIST: {"lookupString":"getOrPut","tailText":"(key: Any?, defaultValue: () -> Any?) for MutableMap<K, V> in kotlin.collections","typeText":"Any?","icon":"nodes/function.svg","attributes":"bold","allLookupStrings":"getOrPut","itemText":"getOrPut"}