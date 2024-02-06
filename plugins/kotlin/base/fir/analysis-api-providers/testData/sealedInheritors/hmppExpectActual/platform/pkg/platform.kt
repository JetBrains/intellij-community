package pkg

actual sealed class SealedClass(val prop: Int)
actual class SealedClass1 : SealedClass(1)
actual class SealedClass2 : SealedClass(2)
