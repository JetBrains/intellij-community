import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer


@Serializable
class WithExistingCompanionNoBraces {
    companion object/*<# { #>*/
/*<# block [fun   serializer ( ) :   KSerializer < WithExistingCompanionNoBraces >]
} #>*/
}

@Serializable
class WithExistingCompanionWithBraces {
    companion object {

/*<# block [fun   serializer ( ) :   KSerializer < WithExistingCompanionWithBraces >] #>*/
    }
}

@Serializable
class WithExistingCompanionWithDeclarations {
    companion object {
        const val el = 1
/*<# block [fun   serializer ( ) :   KSerializer < WithExistingCompanionWithDeclarations >] #>*/
    }
}
