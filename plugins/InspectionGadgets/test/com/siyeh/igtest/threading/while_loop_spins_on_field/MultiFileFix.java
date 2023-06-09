class F2 extends F1 {
    void test() {
        <warning descr="'while' loop spins on field"><caret>while</warning> (f) {

        }
    }
}
