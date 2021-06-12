package transitiveStory.bottomActual.apiCall

import playground.moduleName
import transitiveStory.apiJvm.beginning.KotlinApiContainer
import transitiveStory.apiJvm.jbeginning.JavaApiContainer

open class Jvm18JApiInheritor : JavaApiContainer() {
    // override var protectedJavaDeclaration = ""
    var callProtectedJavaDeclaration = protectedJavaDeclaration
}

open class Jvm18KApiInheritor : KotlinApiContainer() {
    public override val <!LINE_MARKER("descr='Overrides property in 'KotlinApiContainer''")!>protectedKotlinDeclaration<!> =
        "I'm an overridden Kotlin string in `$this` from `" + moduleName +
                "` and shall be never visible to the other modules except my subclasses."
}

/**
 * Some class which type is lately used in the function.
 *
 */
open class FindMyDocumantationPlease

/**
 * A function using a class type placed right into the same file.
 *
 * @param f The parameter of the type under the investigation
 * */
fun iWantSomeDocumentationFromDokka(<!HIGHLIGHTING("severity='WARNING'; descr='[UNUSED_PARAMETER] Parameter 'f' is never used'")!>f<!>: FindMyDocumantationPlease) {}

fun bottActualApiCaller(k: KotlinApiContainer, s: JavaApiContainer, <!HIGHLIGHTING("severity='WARNING'; descr='[UNUSED_PARAMETER] Parameter 'ij' is never used'")!>ij<!>: Jvm18JApiInheritor, ik: Jvm18KApiInheritor) {
    // val first = k.privateKotlinDeclaration
    // val second = k.packageVisibleKotlinDeclaration
    // val third = k.protectedKotlinDeclaration
    val <!HIGHLIGHTING("severity='WARNING'; descr='[UNUSED_VARIABLE] Variable 'fourth' is never used'")!>fourth<!> = ik.protectedKotlinDeclaration
    val <!HIGHLIGHTING("severity='WARNING'; descr='[UNUSED_VARIABLE] Variable 'fifth' is never used'")!>fifth<!> = k.publicKotlinDeclaration
    val <!HIGHLIGHTING("severity='WARNING'; descr='[UNUSED_VARIABLE] Variable 'sixth' is never used'")!>sixth<!> = KotlinApiContainer.publicStaticKotlinDeclaration

    // val seventh = s.privateJavaDeclaration
    // val eighth = s.packageVisibleJavaDeclaration
    val <!HIGHLIGHTING("severity='WARNING'; descr='[UNUSED_VARIABLE] Variable 'ninth' is never used'")!>ninth<!> = s.publicJavaDeclaration
    val <!HIGHLIGHTING("severity='WARNING'; descr='[UNUSED_VARIABLE] Variable 'tenth' is never used'")!>tenth<!> = JavaApiContainer.publicStaticJavaDeclaration
    // val eleventh = ij.protectedJavaDeclaration
}