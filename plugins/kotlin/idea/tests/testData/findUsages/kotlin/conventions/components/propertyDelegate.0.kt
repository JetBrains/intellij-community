// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

val iHaveComponentKey by KeyDelegate<IHaveComponent>()

class KeyDelegate<Value> : ReadOnlyProperty<Any?, Key<Value>> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Key<Value> = Key()
}

class IHaveComponent {
    operator fun comp<caret>onent1(): String? = null
}

fun componentUsageSpace() {
    val (root) = iHaveComponentKey.get()
}

class Key<Value> {
    fun get(): Value = null as Value
}


