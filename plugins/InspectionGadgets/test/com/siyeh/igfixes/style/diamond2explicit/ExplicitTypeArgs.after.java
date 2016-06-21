class Foo<T> {
        <U> Foo(T t) {}
}

class Test {
        public static void main(String[] args) {
                Foo<String> c = new <Integer>Foo<String>("");
        }
}