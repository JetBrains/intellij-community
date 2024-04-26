// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// GROUPING_RULES: org.jetbrains.kotlin.idea.base.searching.usages.KotlinDeclarationGroupingRule
// OPTIONS: usages
fun gg(u: () -> Unit, u1: () -> Unit ) {}
fun ll() {
    gg( {
            print45("")
        }, {
            print45("")
            print45("")
        })
}
fun <caret>print45(s: String) {}