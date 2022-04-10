package sealed;

sealed interface RootWithInterfaceOuter permits ClassImplements, InterfaceNonSealed, ClassNonSealedExtendsImplements {
}