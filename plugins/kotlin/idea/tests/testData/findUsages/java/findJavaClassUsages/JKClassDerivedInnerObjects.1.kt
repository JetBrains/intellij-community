class Outer {
    @Suppress("LOCAL_INTERFACE_NOT_ALLOWED", "INTERFACE_WITH_SUPERCLASS")
    public interface T : A

    public object O1 : A()

    class Inner {
        public object O2 : T
    }
}
