package kotlinpackage.two

import kotlin.reflect.KProperty

class Some(var value: String)
operator fun Some.getValue(thisObj: Any?, property: KProperty<*>): String = value
operator fun Some.setValue(thisObj: Any?, property: KProperty<*>, value: String) {
    this.value = value
}