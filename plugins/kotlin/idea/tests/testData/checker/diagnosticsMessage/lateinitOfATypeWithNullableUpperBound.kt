// FIR_IDENTICAL

class C<V>() {
    <error descr="[INAPPLICABLE_LATEINIT_MODIFIER]">lateinit</error> var item: V
}
