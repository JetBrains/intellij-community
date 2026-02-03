sealed interface SealedInterface

class DirectInheritor : SealedInterface

abstract class DirectAbstractInheritor : SealedInterface

class IndirectInheritor1 : DirectAbstractInheritor()
class IndirectInheritor2 : DirectAbstractInheritor()

sealed class DirectSealedInheritor : SealedInterface {
    object IndirectInheritor3 : DirectSealedInheritor()
}
