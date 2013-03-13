package com.siyeh.igtest.abstraction.weaken_type;

public class AutoClosableTest
{
    public static class Foo
    {
        public void go() {}
    }

    public static class Bar extends Foo implements AutoCloseable
    {
        @Override
        public void close() {}
    }

    public static void test()
    {
        try (Bar bar = new Bar()) {
            bar.go();
        }
    }
}
class AutoClosableTest2
{
    public static class Foo implements AutoCloseable
    { 
        public void close() {}
        public void go() {}
    }

    public static class Bar extends Foo {}

    public static void test() {
        try (Bar bar = new Bar()) {
            bar.go();
        }
    }

    void dodo() throws java.io.IOException {
        try (java.io.Reader  reader = new java.io.FileReader("/home/steve/foo.txt")) {
            System.out.println(reader);
        }
    }
}