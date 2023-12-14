// IGNORE_K2
fun test() {
    buildList {
        buildList<Int> {
            buildList<String> {
                addAll<caret>
            }
        }
    }
}

// EXIST: {"lookupString":"addAll","tailText":"(elements: Iterable<T>) for MutableCollection<in T> in kotlin.collections"}
// EXIST: {"lookupString":"addAll","tailText":"(elements: Sequence<T>) for MutableCollection<in T> in kotlin.collections"}
// EXIST: {"lookupString":"addAll","tailText":"(elements: Array<out T>) for MutableCollection<in T> in kotlin.collections"}
// EXIST: {"lookupString":"addAll","tailText":"(elements: Collection<E>)"}
// EXIST: {"lookupString":"addAll","tailText":"(index: Int, elements: Collection<E>)"}