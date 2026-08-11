// COMPILER_ARGUMENTS: -XXLanguage:+SealedInterfaces -XXLanguage:+AllowSealedInheritorsInDifferentFilesOfSamePackage
package sealed.otherpackage
import sealed.SealedDeclarationInterface
import sealed.SealedDeclarationClass

class D: <error descr="[SEALED_INHERITOR_IN_DIFFERENT_PACKAGE]">SealedDeclarationInterface</error>
class DClass: <error descr="[SEALED_INHERITOR_IN_DIFFERENT_PACKAGE]">SealedDeclarationClass</error>()
