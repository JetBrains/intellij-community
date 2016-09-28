package com.siyeh.igtest.performance.method_may_be_static;


import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;

public class A implements Serializable {

    private void writeObject(java.io.ObjectOutputStream out)
            throws IOException {
        System.out.println("out");
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        System.out.println();
    }

    Object writeReplace() throws ObjectStreamException {
        return null;
    }

    Object readResolve() throws ObjectStreamException {
        return null;
    }
    native void f();
    void <warning descr="Method 'g()' may be 'static'">g</warning>() {
        System.out.println("boo!");
    }
}
class C {
  public int getInt() { return 5; }
}
class D extends C implements Surprise {
}
interface Surprise {
  int getInt();
}

interface FromJava8 {
  default void <warning descr="Method 'foo()' may be 'static'">foo</warning>() {
    System.out.println();
  }
}
class B {
  public void accept(String t) {
    System.out.println(t);
  }
}
class V extends B implements Consumer<String> {}
interface Consumer<T> {
  void accept(T t);
}