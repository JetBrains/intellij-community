class A {
    int j

    def r() {
        int r = <caret>getJ()  //synthetic getter serach

        setJ(0)
    }
}
