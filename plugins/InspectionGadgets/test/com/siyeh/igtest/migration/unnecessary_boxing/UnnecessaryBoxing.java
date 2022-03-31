package com.siyeh.igtest.migration.unnecessary_boxing;




public class UnnecessaryBoxing {

    Integer foo(String foo, Integer bar) {
        return foo == null ? Integer.valueOf(0) : bar;
    }

    public static void main(String[] args)
    {
        final Integer intValue = <warning descr="Unnecessary boxing">new Integer</warning>(3);
        final Long longValue = <warning descr="Unnecessary boxing">new Long</warning>(3L);
        final Long longValue2 = <warning descr="Unnecessary boxing">new Long</warning>(3);
        final Short shortValue = <warning descr="Unnecessary boxing">new Short</warning>((short)3);
        final Double doubleValue = <warning descr="Unnecessary boxing">new Double</warning>(3.0);
        final Float floatValue = <warning descr="Unnecessary boxing">new Float</warning>(3.0F);
        final Byte byteValue = <warning descr="Unnecessary boxing">new Byte</warning>((byte)3);
        final Boolean booleanValue = <warning descr="Unnecessary boxing">new Boolean</warning>(true);
        final Character character = <warning descr="Unnecessary boxing">new Character</warning>('c');
    }

    Integer foo2(String foo, int bar) {
        return foo == null ? <warning descr="Unnecessary boxing">Integer.valueOf</warning>(0) : bar;
    }

    void additionalInnerBoxing(String str) {
      short s = <warning descr="Redundant boxing, 'Short.parseShort()' call can be used instead">Short.valueOf</warning>(str);
      int i = <warning descr="Redundant boxing, 'Integer.parseInt()' call can be used instead">Integer.valueOf</warning>(str);
      long l = <warning descr="Redundant boxing, 'Long.parseLong()' call can be used instead">Long.valueOf</warning>(str);
      double d = <warning descr="Redundant boxing, 'Double.parseDouble()' call can be used instead">Double.valueOf</warning>(str);
      float f = <warning descr="Redundant boxing, 'Float.parseFloat()' call can be used instead">Float.valueOf</warning>(str);
      boolean bool = <warning descr="Redundant boxing, 'Boolean.parseBoolean()' call can be used instead">Boolean.valueOf</warning>(str);
      byte b = <warning descr="Redundant boxing, 'Byte.parseByte()' call can be used instead">Byte.valueOf</warning>(str);
    }

    short parseShort(String id) {
      return <warning descr="Redundant boxing, 'Short.parseShort()' call can be used instead">Short.valueOf</warning>(id);
    }

    int parseInt(String id) {
      return <warning descr="Redundant boxing, 'Integer.parseInt()' call can be used instead">Integer.valueOf</warning>(id);
    }

    long parseLong(String id) {
      return <warning descr="Redundant boxing, 'Long.parseLong()' call can be used instead">Long.valueOf</warning>(id);
    }

    double parseDouble(String id) {
      return <warning descr="Redundant boxing, 'Double.parseDouble()' call can be used instead">Double.valueOf</warning>(id);
    }

    float parseFloat(String id) {
      return <warning descr="Redundant boxing, 'Float.parseFloat()' call can be used instead">Float.valueOf</warning>(id);
    }

    boolean parseBoolean(String id) {
      return <warning descr="Redundant boxing, 'Boolean.parseBoolean()' call can be used instead">Boolean.valueOf</warning>(id);
    }

    byte parseByte(String id) {
      return <warning descr="Redundant boxing, 'Byte.parseByte()' call can be used instead">Byte.valueOf</warning>(id);
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
    g(((<warning descr="Unnecessary boxing">Integer.valueOf</warning>(1))));
    g(((<warning descr="Unnecessary boxing">new Integer</warning>(1))));
  }

  boolean m(@org.jetbrains.annotations.NotNull Boolean p) {
    Boolean o = null;
    boolean b = o != Boolean.valueOf(false) || p != Boolean.valueOf(false); // object comparison
    return b == <warning descr="Unnecessary boxing">Boolean.valueOf</warning>(false);
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

  void testSynchronized() {
    synchronized (Integer.valueOf(123)) {
      System.out.println("hello");
    }
  }

  void testVar() {
    var x = Integer.valueOf(5);
    Integer y = <warning descr="Unnecessary boxing">Integer.valueOf</warning>(5);
    System.out.println(x.getClass());
    System.out.println(y.getClass());
  }

  int testSwitchExpression(int x) {
    return switch(x) {
      default -> <warning descr="Unnecessary boxing">Integer.valueOf</warning>(x);
    };
  }
}