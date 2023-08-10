package sealed;

sealed interface RootWithInterfaceInnerAndOuter permits RootWithInterfaceInnerAndOuter.Inner, ClassNonSealed {
  final class Inner implements RootWithInterfaceInnerAndOuter {
  }
}