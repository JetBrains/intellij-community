sealed <error descr="[REDUNDANT_MODIFIER]">abstract</error> class First

<error descr="[REDUNDANT_MODIFIER]">abstract</error> sealed class Second

abstract class Base {
    abstract <error descr="[REDUNDANT_MODIFIER]">open</error> fun foo()

    <error descr="[REDUNDANT_MODIFIER]">open</error> abstract val name: String
}
