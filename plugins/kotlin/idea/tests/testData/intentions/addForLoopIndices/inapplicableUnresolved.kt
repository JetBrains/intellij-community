// IS_APPLICABLE: false
// ERROR: Unresolved reference: b
// K2_ERROR: Method 'iterator()' is ambiguous for this expression. Applicable candidates:<br>fun <T> Enumeration<T>.iterator(): Iterator<T><br>fun <T> Iterator<T>.iterator(): Iterator<T><br>fun <K, V> Map<out K, V>.iterator(): Iterator<Map.Entry<K, V>><br>fun <K, V> MutableMap<K, V>.iterator(): MutableIterator<MutableMap.MutableEntry<K, V>><br>fun CharSequence.iterator(): CharIterator<br>fun BufferedInputStream.iterator(): ByteIterator
// K2_ERROR: Unresolved reference 'b'.
fun foo() {
    for (a <caret>in b) {

    }
}