package pkg

actual sealed class SealedClass(val prop: Int)
actual class SealedClass1 : SealedClass(1)
// SealedClass2 is not actualized, because it is declared in the intermediate source set.
// The JS source set has no dependsOn relation with the intermediate source set.
class SealedClassJs : SealedClass(4)

actual sealed interface SealedInterface
actual interface SealedInterface1 : SealedInterface
class SealedInterfaceJsImpl : SealedInterface