// WITH_STDLIB
fun test() {
    try {
        // Difference with K1: error messages text is different
        <error descr="[ITERATOR_AMBIGUITY] Method 'iterator()' is ambiguous for this expression. Applicable candidates:
fun <T> Enumeration<T>.iterator(): Iterator<T>
@InlineOnly() fun <T> @receiver:InlineOnly() Iterator<T>.iterator(): Iterator<T>
@InlineOnly() fun <K, V> @receiver:InlineOnly() Map<out K, V>.iterator(): Iterator<Map.Entry<K, V>>
@JvmName(...) @InlineOnly() fun <K, V> @receiver:JvmName(...) @receiver:InlineOnly() MutableMap<K, V>.iterator(): MutableIterator<MutableMap.MutableEntry<K, V>>
fun CharSequence.iterator(): CharIterator
fun BufferedInputStream.iterator(): ByteIterator">for(<error descr="Expecting a variable name">)</error>
    </error><error descr="Expecting an expression">}</error>
    catch (x: Throwable) {

    }
}