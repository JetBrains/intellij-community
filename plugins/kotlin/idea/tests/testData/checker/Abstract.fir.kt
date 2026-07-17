package abstract

class MyClass() {
    //properties

    <error descr="[MUST_BE_INITIALIZED_OR_BE_ABSTRACT]">val a: Int</error>
    val a1: Int = 1
    <error descr="[ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS]">abstract</error> val a2: Int
    <error descr="[ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS]">abstract</error> val a3: Int = 1

    <error descr="[MUST_BE_INITIALIZED_OR_BE_ABSTRACT]">var b: Int</error>                private set
    var b1: Int = 0;                         private set
    <error descr="[ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS]">abstract</error> var b2: Int      private set
    <error descr="[ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS]">abstract</error> var b3: Int = 0; private set

    <error descr="[MUST_BE_INITIALIZED]">var c: Int</error>                set(v: Int) { field = v }
    var c1: Int = 0;                         set(v: Int) { field = v }
    <error descr="[ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS]">abstract</error> var c2: Int      set(v: Int) { field = v }
    <error descr="[ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS]">abstract</error> var c3: Int = 0; set(v: Int) { field = v }

    val e: Int                               get() = a
    val e1: Int = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD]">0</error>;          get() = a
    <error descr="[ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS]">abstract</error> val e2: Int      get() = a
    <error descr="[ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS]">abstract</error> val e3: Int = 0; get() = a

    //methods

    <error descr="[NON_ABSTRACT_FUNCTION_WITH_NO_BODY]">fun f()</error>
    fun g() {}
    <error descr="[ABSTRACT_FUNCTION_IN_NON_ABSTRACT_CLASS]">abstract</error> fun h()
    <error descr="[ABSTRACT_FUNCTION_IN_NON_ABSTRACT_CLASS]"><error descr="[ABSTRACT_FUNCTION_WITH_BODY]">abstract</error></error> fun j() {}
}

abstract class MyAbstractClass() {
    //properties

    <error descr="[MUST_BE_INITIALIZED_OR_BE_ABSTRACT]">val a: Int</error>
    val a1: Int = 1
    abstract val a2: Int
    abstract val a3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">1</error>

    <error descr="[MUST_BE_INITIALIZED_OR_BE_ABSTRACT]">var b: Int</error>                private set
    var b1: Int = 0;                         private set
    abstract var b2: Int                     <error descr="[PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY]">private</error> set
    abstract var b3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY]">private</error> set

    <error descr="[MUST_BE_INITIALIZED]">var c: Int</error>                set(v: Int) { field = v }
    var c1: Int = 0;                         set(v: Int) { field = v }
    abstract var c2: Int                     <error descr="[ABSTRACT_PROPERTY_WITH_SETTER]">set(v: Int) { field = v }</error>
    abstract var c3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[ABSTRACT_PROPERTY_WITH_SETTER]">set(v: Int) { field = v }</error>

    val e: Int                               get() = a
    val e1: Int = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD]">0</error>;          get() = a
    abstract val e2: Int                     <error descr="[ABSTRACT_PROPERTY_WITH_GETTER]">get() = a</error>
    abstract val e3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[ABSTRACT_PROPERTY_WITH_GETTER]">get() = a</error>

    //methods

    <error descr="[NON_ABSTRACT_FUNCTION_WITH_NO_BODY]">fun f()</error>
    fun g() {}
    abstract fun h()
    <error descr="[ABSTRACT_FUNCTION_WITH_BODY]">abstract</error> fun j() {}
}

interface MyInterface {
    //properties

    val a: Int
    val a1: Int = <error descr="[PROPERTY_INITIALIZER_IN_INTERFACE]">1</error>
    abstract val a2: Int
    abstract val a3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">1</error>

    var b: Int                                                  <error descr="[PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY]">private</error> set
    var b1: Int = <error descr="[PROPERTY_INITIALIZER_IN_INTERFACE]">0</error>;                             <error descr="[PRIVATE_SETTER_FOR_OPEN_PROPERTY]">private</error> set
    abstract var b2: Int                     <error descr="[PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY]">private</error> set
    abstract var b3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY]">private</error> set

    <error descr="[BACKING_FIELD_IN_INTERFACE]">var c: Int</error>                                   set(v: Int) { field = v }
    <error descr="[BACKING_FIELD_IN_INTERFACE]">var c1: Int</error> = <error descr="[PROPERTY_INITIALIZER_IN_INTERFACE]">0</error>;              set(v: Int) { field = v }
    abstract var c2: Int                     <error descr="[ABSTRACT_PROPERTY_WITH_SETTER]">set(v: Int) { field = v }</error>
    abstract var c3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[ABSTRACT_PROPERTY_WITH_SETTER]">set(v: Int) { field = v }</error>

    val e: Int                                                  get() = a
    val e1: Int = <error descr="[PROPERTY_INITIALIZER_IN_INTERFACE]">0</error>;                             get() = a
    abstract val e2: Int                     <error descr="[ABSTRACT_PROPERTY_WITH_GETTER]">get() = a</error>
    abstract val e3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[ABSTRACT_PROPERTY_WITH_GETTER]">get() = a</error>

    //methods

    fun f()
    fun g() {}
    abstract fun h()
    <error descr="[ABSTRACT_FUNCTION_WITH_BODY]">abstract</error> fun j() {}
}

enum class MyEnum() {
    ;
    //properties

    <error descr="[MUST_BE_INITIALIZED_OR_BE_ABSTRACT]">val a: Int</error>
    val a1: Int = 1
    abstract val a2: Int
    abstract val a3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">1</error>

    <error descr="[MUST_BE_INITIALIZED_OR_BE_ABSTRACT]">var b: Int</error>                private set
    var b1: Int = 0;                         private set
    abstract var b2: Int                     <error descr="[PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY]">private</error> set
    abstract var b3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY]">private</error> set

    <error descr="[MUST_BE_INITIALIZED]">var c: Int</error>                set(v: Int) { field = v }
    var c1: Int = 0;                         set(v: Int) { field = v }
    abstract var c2: Int                     <error descr="[ABSTRACT_PROPERTY_WITH_SETTER]">set(v: Int) { field = v }</error>
    abstract var c3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[ABSTRACT_PROPERTY_WITH_SETTER]">set(v: Int) { field = v }</error>

    val e: Int                               get() = a
    val e1: Int = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD]">0</error>;          get() = a
    abstract val e2: Int                     <error descr="[ABSTRACT_PROPERTY_WITH_GETTER]">get() = a</error>
    abstract val e3: Int = <error descr="[ABSTRACT_PROPERTY_WITH_INITIALIZER]">0</error>; <error descr="[ABSTRACT_PROPERTY_WITH_GETTER]">get() = a</error>

    //methods

    <error descr="[NON_ABSTRACT_FUNCTION_WITH_NO_BODY]">fun f()</error>
    fun g() {}
    abstract fun h()
    <error descr="[ABSTRACT_FUNCTION_WITH_BODY]">abstract</error> fun j() {}
}

abstract enum class MyAbstractEnum() {}

//properties

<error descr="[MUST_BE_INITIALIZED]">val a: Int</error>
val a1: Int = 1
abstract val a2: Int
abstract val a3: Int = 1

<error descr="[MUST_BE_INITIALIZED]">var b: Int</error>                private set
var b1: Int = 0;                         private set
abstract var b2: Int      private set
abstract var b3: Int = 0; private set

<error descr="[MUST_BE_INITIALIZED]">var c: Int</error>                set(v: Int) { field = v }
var c1: Int = 0;                         set(v: Int) { field = v }
abstract var c2: Int      set(v: Int) { field = v }
abstract var c3: Int = 0; set(v: Int) { field = v }

val e: Int                               get() = a
val e1: Int = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD]">0</error>;          get() = a
abstract val e2: Int      get() = a
abstract val e3: Int = 0; get() = a

//methods

<error descr="[NON_MEMBER_FUNCTION_NO_BODY]">fun f()</error>
fun g() {}
abstract fun h()
abstract fun j() {}

//creating an instance
abstract class B1(
    val i: Int,
    val s: String
) {
}

class B2() : B1(1, "r") {}

abstract class B3(i: Int) {
}

fun foo(a: B3) {
    val a = <error descr="[CREATING_AN_INSTANCE_OF_ABSTRACT_CLASS]">B3(1)</error>
    val b = <error descr="[CREATING_AN_INSTANCE_OF_ABSTRACT_CLASS]">B1(2, "s")</error>
}
