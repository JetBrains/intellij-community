sealed <warning descr="[REDUNDANT_MODIFIER]">abstract</warning> class First

<warning descr="[REDUNDANT_MODIFIER]">abstract</warning> sealed class Second

abstract class Base {
    abstract <warning descr="[REDUNDANT_MODIFIER]">open</warning> fun foo()

    <warning descr="[REDUNDANT_MODIFIER]">open</warning> abstract val name: String
}
