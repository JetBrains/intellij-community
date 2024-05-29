// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "width: Int"

data class DataClass(val wi<caret>dth: Int) : Comparable<DataClass> {
    override fun compareTo(other: DataClass): Int {
        return this.width - other.width
    }
}

fun main() {
    DataClass(1).compareTo(DataClass(0))
}