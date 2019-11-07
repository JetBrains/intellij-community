class A {
    int j

    int getJ() { j }

    def r() {
        int r = <caret>getJ()
    }
}
