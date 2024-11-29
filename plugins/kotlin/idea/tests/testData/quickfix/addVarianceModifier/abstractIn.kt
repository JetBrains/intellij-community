// "Add 'in' variance" "true"
abstract class AbstractIn<<caret>T>(private val foo: T) {
    fun bar(arg: T) = foo == arg
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddVarianceModifierInspection$AddVarianceFix