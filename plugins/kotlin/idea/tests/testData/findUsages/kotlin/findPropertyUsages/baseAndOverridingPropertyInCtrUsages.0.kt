// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "openProp: String"

open class BaseMemberPropertyClass(open var openProp: String)

class MemberPropertyClass(override var openProp<caret>: String) : BaseMemberPropertyClass("dummy")

fun testMemberConcreteProperty(b: BaseMemberPropertyClass, m: MemberPropertyClass) {
    val c = b.openProp //not found by K2 KTIJ-26343
    val d = m.openProp
    b.openProp = "" //not found by K2 KTIJ-26343
    m.openProp = ""
}
