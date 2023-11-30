// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "var openProp"

open class BaseMemberPropertyClass(open var openProp: String)

class MemberPropertyClass(openProp: String) : BaseMemberPropertyClass("dummy") {
    override var openProp<caret> = openProp
}

fun testMemberConcreteProperty(b: BaseMemberPropertyClass, m: MemberPropertyClass) {
    val c = b.openProp
    val d = m.openProp
    b.openProp = ""
    m.openProp = ""
}
