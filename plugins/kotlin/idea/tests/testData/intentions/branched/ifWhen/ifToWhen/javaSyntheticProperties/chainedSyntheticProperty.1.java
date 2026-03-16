public class Outer {
    private Inner inner;

    public Outer(Inner inner) {
        this.inner = inner;
    }

    public Inner getInner() {
        return inner;
    }

    public static class Inner {
        public enum Type { A, B, C }
        private Type type;

        public Inner(Type type) {
            this.type = type;
        }

        public Type getType() {
            return type;
        }
    }
}
