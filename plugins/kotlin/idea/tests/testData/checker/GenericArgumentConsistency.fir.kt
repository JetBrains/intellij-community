interface A<in T> {}
interface B<T> : A<Int> {}
interface C<T> : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">B<T>, A<T></error> {}
interface C1<T> : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">B<T>, A<Any></error> {}
interface D : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]"><error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">C<Boolean>, B<Double></error></error>{}

interface A1<out T> {}
interface B1 : A1<Int> {}
interface B2 : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">A1<Any>, B1</error> {}

interface BA1<T> {}
interface BB1 : BA1<Int> {}
interface BB2 : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">BA1<Any>, BB1</error> {}


//package x {
    interface xAA1<out T> {}
    interface xAB1 : xAA1<Int> {}
    interface xAB3 : xAA1<Comparable<Int>> {}
    interface xAB2 : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">xAA1<Number>, xAB1, xAB3</error> {}
//}

//package x2 {
    interface x2AA1<out T> {}
    interface x2AB1 : x2AA1<Any> {}
    interface x2AB3 : x2AA1<Comparable<Int>> {}
    interface x2AB2 : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">x2AA1<Number>, x2AB1, x2AB3</error> {}
//}

//package x3 {
    interface x3AA1<in T> {}
    interface x3AB1 : x3AA1<Any> {}
    interface x3AB3 : x3AA1<Comparable<Int>> {}
    interface x3AB2 : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">x3AA1<Number>, x3AB1, x3AB3</error> {}
//}

//package sx2 {
    interface sx2AA1<in T> {}
    interface sx2AB1 : sx2AA1<Int> {}
    interface sx2AB3 : sx2AA1<Comparable<Int>> {}
    interface sx2AB2 : <error descr="[INCONSISTENT_TYPE_PARAMETER_VALUES]">sx2AA1<Number>, sx2AB1, sx2AB3</error> {}
//}
