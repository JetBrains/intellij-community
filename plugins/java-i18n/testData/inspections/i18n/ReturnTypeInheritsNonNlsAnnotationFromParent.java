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
<error descr="Cyclic inheritance involving 'C'">class Circular implements C</error> {
    public String foo() {
        return <warning descr="Hardcoded string literal: \"text\"">"text"</warning>;
    }
}
<error descr="Cyclic inheritance involving 'C'">interface C extends D</error> {
    public String foo();
}
<error descr="Cyclic inheritance involving 'D'">interface D extends C</error> {
    public String foo();
}