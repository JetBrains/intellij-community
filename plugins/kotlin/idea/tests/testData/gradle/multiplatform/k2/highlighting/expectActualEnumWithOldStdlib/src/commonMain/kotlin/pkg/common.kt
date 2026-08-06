//region Test configuration
// - hidden: line markers
//endregion
package pkg

expect enum class ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN
}

fun useByteOrder(order: ByteOrder): String = order.name
