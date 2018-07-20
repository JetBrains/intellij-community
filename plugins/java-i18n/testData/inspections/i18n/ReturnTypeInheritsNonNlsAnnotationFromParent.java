interface I {
    @org.jetbrains.annotations.NonNls String foo();
}

class B implements I{
    public String foo() {
        return "text";
    }
}

class A {
    B inner = new B() {
        public String foo() {
            return "text";
        }
    };
}