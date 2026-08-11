interface A<T> {}
interface B<T> {}
interface C<T> {}
interface D<T> {}

interface Test : A<<error descr="[PROJECTION_IN_IMMEDIATE_ARGUMENT_TO_SUPERTYPE]">in</error> Int>, B<<error descr="[PROJECTION_IN_IMMEDIATE_ARGUMENT_TO_SUPERTYPE]">out</error> Int>, C<<error descr="[PROJECTION_IN_IMMEDIATE_ARGUMENT_TO_SUPERTYPE]">*</error>>??<error descr="[NULLABLE_SUPERTYPE]">?</error>, D<Int> {}
