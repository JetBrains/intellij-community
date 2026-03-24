// "Add use-site target 'delegate'" "true"
// K2_ERROR: This annotation is not applicable to target 'top level property with delegate'. Applicable targets: field
import kotlin.reflect.KProperty

@Target(AnnotationTarget.FIELD)
annotation class Anno

class Abcd {
    operator fun getValue(nothing: Nothing?, property: KProperty<*>): Any = Unit
    operator fun setValue(nothing: Nothing?, property: KProperty<*>, any: Any) = Unit
}

@<caret>Anno
var a by Abcd()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationUseSiteTargetFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongAnnotationTargetFixFactories$AddAnnotationUseSiteTargetFix