class A {
    def pty
}

class B {
    def pty

    B() {
        new A().with {
            this.pty = pt<ref>y
        }

    }
}