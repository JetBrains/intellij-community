package com.siyeh.igtest.migration.unnecessary_boxing;




public class UnnecessaryBoxing {

    Integer foo(String foo, Integer bar) {
        return foo == null ? Integer.valueOf(0) : bar;
    }

    public static void main(String[] args)
    {
        final Integer intValue = <warning descr="Unnecessary boxing 'new Integer(3)'">new Integer(3)</warning>;
        final Long longValue = <warning descr="Unnecessary boxing 'new Long(3L)'">new Long(3L)</warning>;
        final Long longValue2 = <warning descr="Unnecessary boxing 'new Long(3)'">new Long(3)</warning>;
        final Short shortValue = <warning descr="Unnecessary boxing 'new Short((short)3)'">new Short((short)3)</warning>;
        final Double doubleValue = <warning descr="Unnecessary boxing 'new Double(3.0)'">new Double(3.0)</warning>;
        final Float floatValue = <warning descr="Unnecessary boxing 'new Float(3.0F)'">new Float(3.0F)</warning>;
        final Byte byteValue = <warning descr="Unnecessary boxing 'new Byte((byte)3)'">new Byte((byte)3)</warning>;
        final Boolean booleanValue = <warning descr="Unnecessary boxing 'new Boolean(true)'">new Boolean(true)</warning>;
        final Character character = <warning descr="Unnecessary boxing 'new Character('c')'">new Character('c')</warning>;
    }

    Integer foo2(String foo, int bar) {
        return foo == null ? <warning descr="Unnecessary boxing 'Integer.valueOf(0)'">Integer.valueOf(0)</warning> : bar;
    }

    void noUnboxing(Object val) {
        if (val == Integer.valueOf(0)) {

        } else if (Integer.valueOf(1) == val) {}
        boolean b = true;
        Boolean.valueOf(b).toString();
    }

    public Integer getBar() {
        return null;
    }

    void doItNow(UnnecessaryBoxing foo) {
        Integer bla = foo == null ? Integer.valueOf(0) : foo.getBar();
    }

    private int i;

    private String s;

    public <T>T get(Class<T> type) {
        if (type == Integer.class) {
            return (T) new Integer(i);
        } else if (type == String.class) {
            return (T) s;
        }
        return null;
    }
}
class IntIntegerTest {
  public IntIntegerTest(Integer val) {
    System.out.println("behavoiur 1");
  }

  public IntIntegerTest(int val) {
    System.out.println("behavoiur 2");
  }

  public static void f(Integer val) {
    System.out.println("behavoiur 1");
  }

  public static void f(int val) {
    System.out.println("behavoiur 2");
  }

  public static void g(int val) {}

  public IntIntegerTest() {
  }

  public void test() {
    new IntIntegerTest(new Integer(1)); // <-- incorrectly triggered
    f(new Integer(1)); // <-- not triggered
    g(((<warning descr="Unnecessary boxing 'Integer.valueOf(1)'">Integer.valueOf(1)</warning>)));
    g(((<warning descr="Unnecessary boxing 'new Integer(1)'">new Integer(1)</warning>)));
  }

  boolean m(@org.jetbrains.annotations.NotNull Boolean p) {
    Boolean o = null;
    boolean b = o != Boolean.valueOf(false) || p != Boolean.valueOf(false); // object comparison
    return b == <warning descr="Unnecessary boxing 'Boolean.valueOf(false)'">Boolean.valueOf(false)</warning>;
  }
}
class test {
  static abstract class AAbstractLongMap extends java.util.AbstractMap<Long, Long> {
    public Long put(long key, Long value) {
      return null;
    }
  }
  static AAbstractLongMap new_times;
  public static void main(String[] args) {
    new_times.put(1l, new Long(2l));
  }
}

class WithLambdaUnfriendlyOverloads {
  interface GetInt { int get(); }
  interface GetInteger { Integer get(); }

  private void m(GetInt getter) {
    System.out.println(getter);
  }

  private void m(GetInteger getter) {
    System.out.println(getter);
  }

  void test(boolean cond) {
    m(() -> {
      if (cond)
        return (new Integer(42));
      else
        return foo();
    });
    m(() -> cond ? new Integer(42) : foo());
    m(() -> new Integer(42));
    m(() -> 42);
  }

  private <T> T foo() {
    return null;
  }
}