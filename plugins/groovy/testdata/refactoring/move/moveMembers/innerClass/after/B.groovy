public class B {
    Inner i = new Inner();
    public static class Inner {
        public boolean equals(Object o) {
            return o instanceof Inner;
        }
    }
}