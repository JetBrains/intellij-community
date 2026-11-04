import kotlin.reflect.KProperty
// PROBLEM: none
class Foo {
    var d by Delegate(<caret>this)
}

class Delegate<T : Any>(var value: T){
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }
    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}
