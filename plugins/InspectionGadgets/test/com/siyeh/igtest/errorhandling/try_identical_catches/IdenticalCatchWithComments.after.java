class C {
    void foo() {
        try {
            bar();
        } catch (Ex1 e) {
            // unique comment
            /*some more*/
        } catch (Ex2 | Ex3 e) { /*same comment*/
            // duplicate comment
        }
    }

    void bar() throws Ex1, Ex2, Ex3 {}

    static class Ex1 extends Exception {}
    static class Ex2 extends Exception {}
    static class Ex3 extends Exception {}
}