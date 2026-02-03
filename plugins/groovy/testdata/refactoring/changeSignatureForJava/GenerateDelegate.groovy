class C2 extends C {
    void method() {
    }
}

class Usage {
    {
        new C().method();
        new C2().method();
    }
}