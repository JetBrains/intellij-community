// "Create member function 'F.setValue'" "true"
import kotlin.reflect.KProperty

class F {
    operator fun getValue(x: X, property: KProperty<*>): Int = 1
}

class X {
    var f: Int by F()<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix