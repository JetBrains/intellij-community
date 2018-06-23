package com.siyeh.igtest.abstraction.weaken_type;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.io.*;

public class TypeMayBeWeakened {

    void weakness() {
        Map<Integer, String> map = new HashMap<Integer, String>();
        Integer key = 34;
        map.get(key);
    }

    public static String hashes(int len) {
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<len;i++)
            sb.append('#');
        return sb.toString();
    }


    public static int exp(Iterable<String> list) {
        int i = 0;
        for (String s: list) {
            if ("some".equals(s)) {
                i++;
            }
        }
        return i;
    }

    class WeakBoolean {
        private Boolean myBool;

        WeakBoolean() {
            myBool = true;
        }

        public void inverse() {
            myBool = !myBool;
        }

        public void xor(boolean b) {
            myBool ^= b;
        }
    }

    void bar() {
        foo(new WeakBoolean());
    }

    void foo(WeakBoolean <warning descr="Type of parameter 'b' may be weakened to 'java.lang.Object'">b</warning>) {
        System.out.println("b: " + b);
    }

    String foo(String s) {
        return s + 1;
    }

    private static void method() throws IllegalArgumentException {
        try {
            FileInputStream fis=new FileInputStream("/etc/modules");
        }
        catch(FileNotFoundException fnfex) {
            IllegalArgumentException <warning descr="Type of variable 'iaex' may be weakened to 'java.lang.RuntimeException'">iaex</warning>=new IllegalArgumentException("Exception Message");
            iaex.initCause(fnfex);
            throw iaex;
        }
    }

    public static void method(String weakened) {
        acceptsArray(new String[] {weakened});
    }

    public static void acceptsArray(String[] sarray) {}

    class Test {
        int x;
        void foo() { Test f = new Test(); f.x++; }
    }

    void listy(ArrayList <warning descr="Type of parameter 'list' may be weakened to 'java.lang.Iterable'">list</warning>) {
        for (Object o : list) {

        }
    }

    void construct() {
        final TypeMayBeWeakened var = new TypeMayBeWeakened();
        var.new Test();
    }

    class Param2Weak<T> {
        public void method(T p) {}
    }

    public class Weak2Null extends Param2Weak<String> {
        public void context(String p) {
            method(p);
        }
    }

    void simpleIf(Boolean condition) {
        if (condition);
    }

    void simpleFor(Boolean condition) {
        for (;condition;);
    }

    void simpleSwitch(Integer value) {
        switch (value) {

        }
    }

    Integer simpleConditional(Boolean condition, Integer value1, Integer value2) {
        return condition ? value1 : value2;
    }

    private static <T, V extends T> java.util.concurrent.atomic.AtomicReference<T> nullSafeReference(java.util.concurrent.atomic.AtomicReference<T> ref, V value) {
        if (ref != null) ref.set(value);
        return ref;
    }
}
class MyClass  {

  public MyClass(java.util.Date date, String[] classNames) {}

  static MyClass readMyClass(final ObjectInputStream <warning descr="Type of parameter 'objectInput' may be weakened to 'com.siyeh.igtest.abstraction.weaken_type.DataInput'">objectInput</warning>) {
    final long time = objectInput.readLong();
    final int size = objectInput.readInt();
    final String[] classNames = new String[size];
    for (int i = 0; i < size; i++) {
      classNames[i] = objectInput.readUTF();
    }
    return new MyClass(new java.util.Date(time), classNames);
  }
}
interface DataInput {
  long readLong();
  int readInt();
  String readUTF();
}
abstract class ObjectInputStream implements DataInput {

  public String readUTF() {
    return null;
  }
}
class Test implements Foo2 {
  void test(Test <warning descr="Type of parameter 't' may be weakened to 'com.siyeh.igtest.abstraction.weaken_type.Foo2'">t</warning>) {
    t.bar();
  }
  public void bar() {
  }
}
@Deprecated
interface Foo {
  void bar();
}
interface Foo2 extends Foo {}
class Helper {

  void foo() {
    class A<T> {
      void foo() {}
    }
    class B<T> extends A<T> {}
    B<String> <warning descr="Type of variable 'b' may be weakened to 'A'">b</warning> = new B();
    b.foo();
  }
}
class MethodReference1 {
  public void m(Set<Integer> list) {
    f(MethodReference1::myTransform);
  }

  void f(java.util.function.Function<Integer, String> function) {}

  private static String myTransform(int in) {
    return Integer.toString(in);
  }
}
class MethodReference2 {
  public void main(String[] args) {
    Runnable r = MethodReference2::myTransform;
    Object o = myTransform();
  }

  private static String <warning descr="Return type of method 'myTransform()' may be weakened to 'java.lang.Object'">myTransform</warning>() {
    return "Integer.toString(in)";
  }
}

class Lambda {
  public static void main(String[] args) throws IOException {
    final File file = new File("");
    try (InputStream inputStream = new FileInputStream(file)) {
      final ToInputStream toInputStream = name -> inputStream;
    }
  }

  interface ToInputStream {
    InputStream map(String name);
  }
}

class LambdaWithBody {
  public static void main(String[] args) throws IOException {
    final File file = new File("");
    try (InputStream inputStream = new FileInputStream(file)) {
      final ToInputStream toInputStream = name -> {
        return inputStream;
      };
    }
  }

  interface ToInputStream {
    InputStream map(String name);
  }
}

class ParensUsage {
  public static void main(String[] args) {
    SortedMap<String, String> s = new TreeMap<>();
    System.out.println(s.keySet());
    System.out.println((s).firstKey());
  }
}