class AAA {
    <caret>AAA(Map m) {}
    AAA() {
        this(t : "")
        new AAA(new HashMap())
    }
}