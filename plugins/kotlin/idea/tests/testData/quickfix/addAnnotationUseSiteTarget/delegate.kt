// "Add use-site target 'delegate'" "true"
import kotlin.reflect.KProperty

@Target(AnnotationTarget.FIELD)
annotation class Anno

class Abcd {
    operator fun getValue(nothing: Nothing?, property: KProperty<*>): Any = Unit
    operator fun setValue(nothing: Nothing?, property: KProperty<*>, any: Any) = Unit
}

@<caret>Anno
var a by Abcd()
