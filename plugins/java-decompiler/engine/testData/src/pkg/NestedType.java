package pkg;

public class NestedType {
    public void doSomething() {
        Foo foo = new Foo();
        Foo.Bar bar = new Foo.Bar();
        System.out.println(foo);
        System.out.println(bar);

        FooBar<String> fooBar = new FooBar<> ();
        System.out.println(foo);
    }

    static class Foo {
        public  void doSomething() {
            Bar fooBar = new Bar();
            FooBar.Bar<String, String> fooBarBar = new FooBar.Bar<>();
            System.out.println(fooBar);
            System.out.println(fooBarBar);
        }

        static class Bar { }
    }

    static class FooBar<T> {
        public  void doSomething() {
            Foo.Bar fooBar = new Foo.Bar();
            Bar<String, String> fooBarBar = new Bar<>();
            System.out.println(fooBar);
            System.out.println(fooBarBar);
        }

        static class Bar<T, E> { }
    }
}