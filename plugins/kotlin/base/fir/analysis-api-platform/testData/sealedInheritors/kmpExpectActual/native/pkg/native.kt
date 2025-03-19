package pkg

actual sealed class SealedClass(val prop: Int)
actual class SealedClass1 : SealedClass(1)
actual class SealedClass2 : SealedClass(2)
class SealedClassNative : SealedClass(5)

actual sealed interface SealedInterface
actual interface SealedInterface1 : SealedInterface
actual class SealedInterface2 : SealedInterface
class SealedInterfaceNativeImpl : SealedInterface
