// AFTER_ERROR: Cannot infer a type for this parameter. Please specify it explicitly.
// AFTER_ERROR: Cannot infer a type for this parameter. Please specify it explicitly.
// K2_AFTER_ERROR: An explicit type is required on a value parameter.
// K2_AFTER_ERROR: An explicit type is required on a value parameter.
// K2_AFTER_ERROR: Function 'component1()' is ambiguous for this expression: <br>fun <T> Array<out T>.component1(): T<br>fun ByteArray.component1(): Byte<br>fun ShortArray.component1(): Short<br>fun IntArray.component1(): Int<br>fun LongArray.component1(): Long<br>fun FloatArray.component1(): Float<br>fun DoubleArray.component1(): Double<br>fun BooleanArray.component1(): Boolean<br>fun CharArray.component1(): Char<br>fun <T> List<T>.component1(): T<br>fun <K, V> Map.Entry<K, V>.component1(): K<br>fun UIntArray.component1(): UInt<br>fun ULongArray.component1(): ULong<br>fun UByteArray.component1(): UByte<br>fun UShortArray.component1(): UShort.
// K2_AFTER_ERROR: Function 'component2()' is ambiguous for this expression: <br>fun <T> Array<out T>.component2(): T<br>fun ByteArray.component2(): Byte<br>fun ShortArray.component2(): Short<br>fun IntArray.component2(): Int<br>fun LongArray.component2(): Long<br>fun FloatArray.component2(): Float<br>fun DoubleArray.component2(): Double<br>fun BooleanArray.component2(): Boolean<br>fun CharArray.component2(): Char<br>fun <T> List<T>.component2(): T<br>fun <K, V> Map.Entry<K, V>.component2(): V<br>fun UIntArray.component2(): UInt<br>fun ULongArray.component2(): ULong<br>fun UByteArray.component2(): UByte<br>fun UShortArray.component2(): UShort.
fun a() {
    val <caret>a = { (a, b // awd
              ,/**/), c, -> }
}