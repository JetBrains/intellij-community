// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: overrides
// PSI_ELEMENT_AS_TITLE: "var absProp: String"


interface KotlinInterface {

    var absProp<caret>: String

}

class KotlinImpl(override var absProp: String) : KotlinInterface

class KotlinImpl2 : KotlinInterface {
    override var absProp: String = ""
}