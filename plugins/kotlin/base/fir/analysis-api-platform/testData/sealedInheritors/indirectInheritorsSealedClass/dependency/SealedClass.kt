sealed class SealedClass

class DirectInheritor : SealedClass()

abstract class DirectAbstractInheritor : SealedClass()

class IndirectInheritor1 : DirectAbstractInheritor()
class IndirectInheritor2 : DirectAbstractInheritor()

sealed class DirectSealedInheritor : SealedClass() {
    object IndirectInheritor3 : DirectSealedInheritor()
}
