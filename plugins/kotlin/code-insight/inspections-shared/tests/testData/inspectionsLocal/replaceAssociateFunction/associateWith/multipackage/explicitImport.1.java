package somepackage.subpackage

public class A {
    public class B extends A {
        public C toC() {
            return new C();
        }
    }
    public class C extends A {}
}
