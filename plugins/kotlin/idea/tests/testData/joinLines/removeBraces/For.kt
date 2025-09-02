// AFTER_ERROR: Overload resolution ambiguity: <br>public inline fun println(message: Any?): Unit defined in kotlin.io<br>public inline fun println(message: Boolean): Unit defined in kotlin.io<br>public inline fun println(message: Byte): Unit defined in kotlin.io<br>public inline fun println(message: Char): Unit defined in kotlin.io<br>public inline fun println(message: CharArray): Unit defined in kotlin.io<br>public inline fun println(message: Double): Unit defined in kotlin.io<br>public inline fun println(message: Float): Unit defined in kotlin.io<br>public inline fun println(message: Int): Unit defined in kotlin.io<br>public inline fun println(message: Long): Unit defined in kotlin.io<br>public inline fun println(message: Short): Unit defined in kotlin.io
// AFTER_ERROR: Unresolved reference: collection
// K2_AFTER_ERROR: Method 'iterator()' is ambiguous for this expression. Applicable candidates:<br>fun <T> Enumeration<T>.iterator(): Iterator<T><br>fun <T> Iterator<T>.iterator(): Iterator<T><br>fun <K, V> Map<out K, V>.iterator(): Iterator<Map.Entry<K, V>><br>fun <K, V> MutableMap<K, V>.iterator(): MutableIterator<MutableMap.MutableEntry<K, V>><br>fun CharSequence.iterator(): CharIterator<br>fun BufferedInputStream.iterator(): ByteIterator
// K2_AFTER_ERROR: Unresolved reference 'collection'.
fun foo() {
    <caret>for (element in collection) {
        println(element)
    }
}
