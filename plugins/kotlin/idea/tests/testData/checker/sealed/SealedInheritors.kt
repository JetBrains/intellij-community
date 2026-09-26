// COMPILER_ARGUMENTS: -XXLanguage:+SealedInterfaces -XXLanguage:+AllowSealedInheritorsInDifferentFilesOfSamePackage
package sealed

class C: SealedDeclarationInterface {}
class CClass: SealedDeclarationClass() {}

class D: SealedDeclarationInterface {
    class E: SealedDeclarationInterface {
        class F: SealedDeclarationInterface
    }
}

class DClass: SealedDeclarationClass() {
    class EClass: SealedDeclarationClass() {
        class FClass: SealedDeclarationClass()
    }
}

fun checkWhenNone(value: SealedDeclarationInterface): Int = <error descr="[NO_ELSE_IN_WHEN]">when</error> (<warning descr="[UNUSED_EXPRESSION]">value</warning>) {
}

fun checkWhenNone(value: SealedDeclarationClass): Int = <error descr="[NO_ELSE_IN_WHEN]">when</error> (<warning descr="[UNUSED_EXPRESSION]">value</warning>) {
}

fun checkWhenOneMissing(value: SealedDeclarationInterface): Int = <error descr="[NO_ELSE_IN_WHEN]">when</error> (value) {
    is SealedDeclarationInterface.A -> 1
    is B -> 2
    is C -> 3
}

fun checkWhenOneMissing(value: SealedDeclarationClass): Int = <error descr="[NO_ELSE_IN_WHEN]">when</error> (value) {
    is SealedDeclarationClass.AClass -> 1
    is BClass -> 2
    is CClass -> 3
}