// "Add 'out' variance" "true"
abstract class AbstractOut<<caret>T> {
    abstract val foo: T
    private var bar = foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.AddVarianceModifierInspection$AddVarianceFix