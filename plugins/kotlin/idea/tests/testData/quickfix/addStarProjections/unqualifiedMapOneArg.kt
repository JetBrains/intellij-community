// "class org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix" "false"
// ERROR: 2 type arguments expected for interface Map<K, out V>
// K2_AFTER_ERROR: 2 type arguments expected for interface Map<K, out V> : Any.
public fun foo(a: Any) {
    a is <caret>Map<Int>
}
