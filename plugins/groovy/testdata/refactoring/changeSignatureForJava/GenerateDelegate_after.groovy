class C2 extends C {
    void method(int i) {
    }
}

class Usage {
    {
        new C().method();
        new C2().method();
    }
}