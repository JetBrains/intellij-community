package sealed;

sealed interface RootWithInterfaceOuter permits FinalImplements, InterfaceNonSealed, ClassNonSealedImplements {
}