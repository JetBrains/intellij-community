class Test {
    public static final Test TEST = new Test("text") {
        public void foo() {}
    };

    public Test(@org.jetbrains.annotations.NonNls String p) {

    }

    public abstract void foo();
}