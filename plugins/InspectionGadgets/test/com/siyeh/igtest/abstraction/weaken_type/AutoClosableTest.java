package com.siyeh.igtest.abstraction.weaken_type;

import java.io.*;

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
        try (Bar <warning descr="Type of variable 'bar' may be weakened to 'com.siyeh.igtest.abstraction.weaken_type.AutoClosableTest2.Foo'">bar</warning> = new Bar()) {
            bar.go();
        }
    }

    void dodo() throws IOException {
        try (Reader  <warning descr="Type of variable 'reader' may be weakened to 'java.io.Closeable'">reader</warning> = new FileReader("/home/steve/foo.txt")) {
            System.out.println(reader);
        }
    }
}
class Sample {

    static class IsNotAutoCloseable {
        public void foo(){

        }

        public void close() throws IOException{}
    }


    static class IsAutoCloseable
      extends IsNotAutoCloseable
      implements AutoCloseable
    {
        public void bar(){

        }

    }

    void baz()
      throws Exception
    {
        try (IsAutoCloseable autoCloseable = new IsAutoCloseable())
        {
            autoCloseable.foo();
        }
    }

}