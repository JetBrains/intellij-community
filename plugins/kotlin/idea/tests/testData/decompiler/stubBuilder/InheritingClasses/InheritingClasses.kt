package a

class InheritingClasses {
    abstract class A(override val c: Int = 1) : C {
        open fun of() = 3
        abstract fun af(): Int
        open val op = 4
        abstract val ap: Int
    }

    open class B : A(2) {
        override fun of() = 4
        override fun af() = 5
        override val op = 5
        override val ap = 5
    }

    interface C {
        val c: Int
    }

    interface D<T> : C {
        override val c: Int
    }

    interface E
    class G : B(), C, D<Int>, E


    class InheritAny {
        interface SomeInterface
        interface SomeInterface2

        class ImplicitAny

        class ExplicitAny : Any()

        class OnlyInterface : SomeInterface
        class OnlyInterfaces : SomeInterface, SomeInterface2

        class InterfaceWithExplicitAny : Any(), SomeInterface
        class InterfacesWithExplicitAny : SomeInterface2, Any(), SomeInterface
    }

    abstract class InheritFunctionType : ((Int, String) -> Int)
}