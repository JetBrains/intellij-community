// FIR_IDENTICAL
// FIR_COMPARISON
import kotlin.collections.toSortedMa<caret>

// EXIST: { "lookupString":"toSortedMap", "tailText":"() for Map<out K, V> in kotlin.collections" }
// EXIST: { "lookupString":"toSortedMap", "tailText":"(comparator: Comparator<in K>) for Map<out K, V> in kotlin.collections" }
// NOTHING_ELSE