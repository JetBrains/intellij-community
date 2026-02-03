class JaKtCtorUse {
    public void use() {
        I i = A::new;
        I i1 = () -> new A();
        I i22 = () -> new A(22);
    }

    interface I {
        A foo();
    }
}

