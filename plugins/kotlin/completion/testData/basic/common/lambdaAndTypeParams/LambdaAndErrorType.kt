fun <K, V> buildMyMap(builderAction: MutableMap<K, V>.() -> Unit): Map<K, V> { TODO()}

fun test() {
    buildMyMap {// this: MutableMap<???, ???>
        put<caret>
    }
}

// EXIST: {"lookupString":"put","tailText":"(key: K, value: V)","typeText":"V?"}
// EXIST: {"lookupString":"putAll","tailText":"(pairs: Iterable<Pair<K, V>>) for MutableMap<in K, in V> in kotlin.collections","typeText":"Unit"}
// EXIST: {"lookupString":"putAll","tailText":"(pairs: Sequence<Pair<K, V>>) for MutableMap<in K, in V> in kotlin.collections","typeText":"Unit"}
// EXIST: {"lookupString":"putAll","tailText":"(pairs: Array<out Pair<K, V>>) for MutableMap<in K, in V> in kotlin.collections","typeText":"Unit"}
// EXIST: {"lookupString":"putAll","tailText":"(from: Map<out K, V>)","typeText":"Unit"}
// EXIST: {"lookupString":"getOrPut","tailText":"(key: Any?, defaultValue: () -> Any?) for MutableMap<K, V> in kotlin.collections","typeText":"Any?"}