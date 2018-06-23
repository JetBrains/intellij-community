package com.siyeh.igtest.style.field_final;

import java.awt.*;
import java.io.*;
import java.util.*;

public class FieldMayBeFinal {

    private static String string;
    private static int i;

    static {
        string = null;
    }
    static {
        string = null;
    }

    private String <warning descr="Field 'other' may be 'final'">other</warning>;
    {
        other = null;
    }
    private String ss;
    {
        ss = "";
    }
    {
        ss = "";
    }

    private int <warning descr="Field 'number' may be 'final'">number</warning>;
    private String s;
    public FieldMayBeFinal() {
        s = "";
        number = 0;
    }

    public FieldMayBeFinal(int number) {
        new Runnable() {

            public void run() {
                s = "";

            }
        };
        s = "";
        this.number = number;
    }

    private String utterance = "asdfas";

    private class Action {
        public void foo() {
            utterance = "boo!";
        }
    }

    private String unused;

    private static class Boom {
        private String notFinal;

        private static String two;

        static {
            if (1 == 2) {
                two = "other";
            }
        }

        Boom(boolean b) {
            if (b) {
                notFinal = "";
            }
        }

    }

    private static boolean flag = true;
    class KeyEvent { boolean isAltDown() { return false; }}
    private static final Object processor = new Object() {
        public boolean postProcessKeyEvent(KeyEvent event) {
            flag = event.isAltDown();
            return false;
        }
    };

    static class Test
    {

        public static void main(String[] args)
        {
            Inner inner = new Inner();
            inner.val = false;
        }

        private static class Inner
        {
            private boolean val = true;
            private boolean <warning descr="Field 'pleaseTellMeIt' may be 'final'">pleaseTellMeIt</warning> = true;
        }
    }

    static class Test3 {
        private static String hostName;
        static {
            try {
                hostName = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                hostName = "localhost";
            }
        }
    }

    static class Test4 {
        private static String <warning descr="Field 'hostName' may be 'final'">hostName</warning>;
        static {
            try {
                hostName = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                throw new RuntimeException();
            }
        }
    }

    static class DoubleAssignment {
        private String result;

        public DoubleAssignment() {
            result = "";
            result = "";
        }
    }

    static class IncrementInInitializers {
        private int i = 0;
        private final int j = i++;
    }

    static class AssignmentInForeach {
        private boolean b, c;
        private int j;

        AssignmentInForeach(int[][] is) {
            b = false;
            for (int i : is[j]) {
                b = c = i == 10;
            }
        }
    }

    static class StaticVariableModifiedInInstanceVariableInitializer {

        private static int COUNT = 0; // <<<<<< highlights as "can be made final"

        private final int count = COUNT++;

    }

    static class FalsePositive1 {
        private int i;

        FalsePositive1() {
            System.out.println(i);
            i = 1;
        }
    }
}
class NotFinal {
    private static final NotFinal INSTANCE = new NotFinal();

    private boolean isInitialized;

    private NotFinal() {
        isInitialized = false;
    }

    public static synchronized void initialize() {
        INSTANCE.isInitialized = true;
    }
}
class AAA {
    private String test;

    public AAA(int num) {
        if(num < 0) {
            return;
        }

        test = "ok";
    }

    public void feep() {
        System.out.println("test = " + test);
    }
}
class X {
    private int x;

    X() {
        x += 1;
    }
}
class XX {
    private int xx;
    XX() {
        if (true) {
            return;
        }
        xx = 1;
    }
}
class Y {
    private int <warning descr="Field 'x' may be 'final'">x</warning>; // can be final
    private int <warning descr="Field 'y' may be 'final'">y</warning>; // can be final
    private int <warning descr="Field 'z' may be 'final'">z</warning> = y = 1; // can be final

    Y() {
        x = 1;
    }

    Y(String s) {
        this();
    }
}
class Z {
    Q q = new Q();
    class Q {
        private int i =1;
    }
    class R {
        {
            q.i = 2;
        }
    }
}
class ZX {
    private int i;

    ZX() {
        if (false && (i = 1) == 1) {

        };
    }
}
class InspectionTest
{
    private Object field;

    public InspectionTest(Object field)
    {
        this.field = field;
    }

    public InspectionTest()
    {
        try
        {
            File file = new File("test");
            if (!file.canRead())
            {
                return;
            }
            file.createNewFile();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        this.field = new Object();
    }
}
class InspectionTest2
{
    private Object field;

    public InspectionTest2(Object field)
    {
        this.field = field;
    }

    public InspectionTest2()
    {
        try
        {
            File file = new File("test");
            file.createNewFile();
            this.field = new Object();
        }
        catch (IOException e)
        {
            this.field = null;
        }
    }
}
class ActionMenu {
    private boolean myMnemonicEnabled;

    public ActionMenu(final boolean enableMnemonics) {
        myMnemonicEnabled = enableMnemonics;
    }

    private boolean isTopLevel() {
        return true;
    }

    public void setMnemonicEnabled(boolean enable) {
        myMnemonicEnabled = enable;
    }
}
class H {
    private int h = 1;

    static void H(H h) {
        h.h = 2;
    }
}
class I  {
    private int i = 1;

    static class J {
        void i(I i) {
            i.i = 1;
        }
    }
}
class J {
    private static int j = 1;

    class K {
        void f() {
            J.j = 2;
        }
    }
}
class K {
    private int k = 1;
    private static int l = new K().k = 2;

    K() {
        l = 2;
    }
}
class L {
    private static int l = 1;

    {
        l = 3;
    }
}
class M {
    private int m = 1;

    static {
        new M().m = 2;
    }
}
class N {
    private int <warning descr="Field 'n' may be 'final'">n</warning>;
    N() {
        if (true && (n = 1) == 1) {}
    }
}
class O {
    private int <warning descr="Field 'o' may be 'final'">o</warning>;
    O() {
        if (false || (o = 1) == 1) {}
    }
}
class P {
    private int p;
    P() {
        if (true || (p = 1) == 1) {}
    }
}


class Q implements Iterator<String> {
    private final String[] strings;
    private int index = 0;
    
    public Q(String[] strings) {
            this.strings = strings;
    }
    
    @Override
    public boolean hasNext() {
            return index < strings.length;
    }
    
    @Override
    public String next() {
            if(!hasNext()) {
                    throw new NoSuchElementException();
            }
    
            return strings[index++].substring(1);
    }
    
    @Override
    public void remove() {
            throw new UnsupportedOperationException();
}
}
class R {
  private static final String someStaticStuff;
  static {
    try {
      someStaticStuff = "";
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String someInjectedStuff;

  public String getSomeInjectedStuff() {
    return someInjectedStuff;
  }
}
class T1 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final, but red when it is
  {
    if (true) {
      (i) = 2;
    } else {
      System.out.println(i);
      i++;
    }
  }
}
class T2 {
  private int <warning descr="Field 'i' may be 'final'">i</warning> ; // may be final
  {
    if ((i = 1) == 1) {
      System.out.println(i);
    }
    System.out.println(i);
  }
}
class T3 {
  private boolean <warning descr="Field 'b' may be 'final'">b</warning>; // may be final
  {
    assert false : "" + (b = true);
    b = true;
    System.out.println(b);
  }
}
class T3a {
  private int x; // can be final as x = 2 is never executed, but we don't see this

  {
    try {
      assert true : x = 2;
    }
    catch (Throwable t) {}
    x = 1;
  }
}
class T3b {
  private int x; // cannot be final

  {
    try {
      assert false : x = 2;
    }
    catch (Throwable t) {}
    x = 1;
  }
}
class T4 {
  private boolean c; // may not be final, but green when it is
  {
    assert c = true : (c) = true; // green, but does not compile
    System.out.println(c);
  }
}
class T5 {
  private boolean <warning descr="Field 'd' may be 'final'">d</warning>; // may be final
  {
    assert true : d = true;
    d = true;
  }
}
class T5a {
  private int <warning descr="Field 'x' may be 'final'">x</warning>; // may be final -- javac accepts this

  {
    x = 1;
    assert true : x = 2;
  }
}
class T5b {
  private int x; // cannot be final

  {
    x = 1;
    assert false : x = 2;
  }
}
class T6 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    switch (i = 5) {}
  }
}
class T7 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    switch (5) {
      default:
        i = 1;
        break;
      case 1:
        i =  3;
        break;
      case 2:
        i = 4;
        System.out.println(i);
        break;
    }
  }
}
class T8 {
  T8() {
    i = 1;
  }

  T8(String s) {
    i = 2;
  }
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
}
class T9 {
  private int <warning descr="Field 'i' may be 'final'">i</warning> = 1; // may be final
}
class T10 {
  private int i = i = 1; // may not be final
}
class T11 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  T11() {
    i = 2;
  }
}
class T12 {
  private int i = 1; // may not be final
  void m() { i = 1;}
}
class T13 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    int j = i = 2;
  }
}
class T14 {
  private int i = 1; // may not final
  class X {{
    i = 2;
  }}
}
class T15 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    class X {{
      System.out.println(i);
    }}
    i = 2;
  }
}
class T16 {
  private int i; // may not be final
  {
    class X {{
      i = 3;
      System.out.println(i);
    }}
    i = 2;
  }
}
class T17 {
  private int i; // may not be final
  {
    new java.util.ArrayList(i);
    i = 23;
  }
}
class T18 {
  private int i; // may not be final
  {
    new Object() {{
      i = 1;
    }};
    i = 2;
  }
}
class T19 {
  private int i; // may not be final, but green when it is
  {
    new Object() {{
      System.out.println(i);
    }};
    i = 1;
  }
}
class T20 {
  private int i; // may not be final
  {
    final Object[] objects = new Object[i];
    i = 2;
  }
}
class T21 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    final Object[] objects = new Object[]{1, 2, i=3};
  }
}
class T23 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    new Object() {
      public String toString() {
        System.out.println(i);
        return null;
      }
    };
    i = 1;
  }
}
class T24 {
  private int i; // may not be final
  private int <warning descr="Field 'j' may be 'final'">j</warning>; // may be final
  {
    i = j = 1;
    i = 2;
  }
}
class T25 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    if (1 == 1) {
      i = 2;
    }
  }
}
class T26 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    if (true && (i = 32) == 30) {}
  }
}
class T27 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    if ((i = 32) == 30 && false) {}
  }
}
class T28 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    do {
    } while(((i = 32) == 30) && false);
  }
}
class T29 {
  private int <warning descr="Field 'j' may be 'final'">j</warning>; // may be final, but code is red when it is
  T29 (int b) {
    do {
      j = 34; // red here
      if (true) break;
    } while (b == 1);
  }
}
class T30 {
  private int i; // may not be final
  {
    if (true || (i = 3) == 4) {}
  }
}
class T31 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    if (false || (i = 3) == 4) {}
  }
}
class T32 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    try {
    } catch (RuntimeException e) {
    } finally {
      i = 1;
    }
  }
}
class T33 {
  private int i; // may not be final
  {
    try {
      i = 1;
    } catch (RuntimeException e) {
      i = 2;
    } finally {
    }
  }
}
class T34 {
  public static class X {
    private int <warning descr="Field 'i' may be 'final'">i</warning> = 1; // may be final
  }
}
class T35 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  private final int j = 3;
  {
    bas: {
      i =  1;
      if (j == 3) {
        break bas;
      }
    }
  }
}
class T36 {
  private S s; // may not be final
  {
    System.out.println(s.t);
    s = null;
  }
  class S {
    public int t;
  }
}
class T37 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    outer: while (true) {
      while (true) {
        i  = 1;
        break outer;
      }
    }
  }
}
class T38 {
  private int i; // may not be final
  {
    int j = 0;
//        i = 1;
    while (j < 8) {
      i = 1;
    }
  }
}
class T39 {
  private int i; // may not be final
  {
    int j = 0;
    do {
      i = 1;
    } while (j == 2);
  }
}
class T40 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    for (;;) {
      i = 1;
      break;
    }
  }
}
class T41 {
  private int i; // may not be final!
  {
    int j = 0;
    for (; j < 9; ) {
      i  = 1;
      break;
    }
  }
}
class T42 {
  private int i; // may not be final
  {
    for (; true ;i = 1) {
      break;
    }
  }
}
class T43 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final!
  {
    for (int j = 0; (i = 1) == 1 && j < 9; j++) {
      break;
    }
  }
}
class T44 {
  private int i; // may not be final
  {
    for (; true ; i = 1, i = 2) {
      i = 2 ;
      break;
    }
  }
}
class T45 {
  // dubious: does not compile in javac when final. Probably javac error - JDK-8198245, but seems logical
  // our behavior is consistent with javac now
  private int i;
  {
    for (; true; i = 1) {
      i = 2;
      break;
    }
  }
}
class T46 {
  private int i; // may not be final
  {
    i = true ? i = 1 : 2;
  }
}
class T47 {
  private Object <warning descr="Field 'o' may be 'final'">o</warning>; // may be final
  {
    boolean b = (o = new Object()) instanceof String;
  }
}
class T48 {
  private String <warning descr="Field 's' may be 'final'">s</warning>; // may be final
  {
    Object o = (Object) (s = "");
  }
}
class T49 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final
  {
    for (String s : g(i = 1)) {
      break;
    }
  }

  java.util.List<String> g(int i) {
    return null;
  }
}
class T50 {
  private boolean b; // may not be final
  T50(int i) {
    if (false && (b = true)) {

    } else {
      b = false;
    }
  }
}
class T51 {
  private boolean <warning descr="Field 'b' may be 'final'">b</warning>; // may be final
  {
    if (true && (b = true)) {
    }
  }
}
class T52 {
  private boolean <warning descr="Field 'z' may be 'final'">z</warning>; // may be final
  {
    boolean y = false;
    if ( true ? (z = y) : false ) {}
  }
}
class T53 {
  private int i; // may not be final
  {
    boolean b = true;
    if (!b || (i = 2) == 2) {
      System.out.println(i);
    }
  }
}
class T54 {
  private boolean <warning descr="Field 'b' may be 'final'">b</warning>; // may be final
  {
    boolean r = false;
    boolean f = false;

    if (!(f || (b = r)))
      r = !b;
    else throw new RuntimeException();
    System.out.println(r);
  }
}
class T55 {
  private int <warning descr="Field 'i' may be 'final'">i</warning>; // may be final, but red when it is
  {
    boolean [] a = new boolean [10];
    a[i = 1] = i > 0;
  }
}
class T56 {
  private boolean b; // may not be final
  {
    if (false && (b = false)) ;
    if (true && (b = false)) ;
  }
}
class T57 {
  private int x; // may be final according to one paragraph of the spec but not in another, and compiles, but let's choose to ignore that.
  {
    x = this.x;
  }
}
class T58 {
  private static int <warning descr="Field 'x' may be 'final'">x</warning>;
  static {
    System.out.println("x = " + T58.x);
    x = 3;
  }
}
class T59 {
  private int i = 0;
  {
    assert true : i++;
  }
}
class T60 {
  private int i = 1;
  {
    if (false) i = 2;
  }
}
class T61 {
  private String <warning descr="Field 's' may be 'final'">s</warning>; // may be final
  T61() throws IOException {
    try (final InputStream is = new FileInputStream(s="ab")) {
    }
  }
}
class T62 {
  private String s; // may not be final
  T62() throws IOException {
    try (final InputStream is = new FileInputStream(s="ab")) {
    }
    s = "ba";
  }
}
class T63 {
  private String s; // may not be final
  T63() throws IOException {
    try (final InputStream is = new FileInputStream(s=s="ab")) {
    }
  }
}
class T64 {
  private String s; // may not be final, but green when it is.
  T64() throws IOException {
    try (final InputStream is = new FileInputStream("ab") {{System.out.println(s);}}) {
    }
    s="";
  }
}
class T65 {
  private Runnable r; // may not be final
  T65() {
    r = () -> System.out.println(r);
  }
}
class T66 {
  private  String <warning descr="Field 's' may be 'final'">s</warning>; // may be final
  T66() {
    s = "10";
    Runnable r = () -> System.out.println(s);
  }
}
class T67 {
  private  String s; // may not be final
  T67() {
    Runnable r = () -> System.out.println(s);
    s = "10";
  }
}
class T68 {
  private Runnable r; // cannot be final in java9, likely it was an error in javac before, compare to T65
  T68() {
    r = () -> System.out.println((this).r);
  }
}
class T69 {
  private String s; // may not be final
  T69() {
    Runnable r = () -> s = "asdf";
  }
}
class T70 {
  private String s; // may not be final
  T70() {
    Runnable r = () -> s = "asdf";
    s = "";
  }
}
class T71 {
  private String s;

  T71() {
    try {
      foo();
    } catch (Throwable t) {
      s = t.getMessage();
    }
  }

  void foo() throws Throwable {}
}
class T72 {
  private boolean b;

  T72(int i) {
    Runnable r = () -> {
      return;
    };
    if (i == 4) b = true;
  }
}
class Foo {

  public interface Accessor<T> {

    T get();

    void set(T value);

  }

  private double myValue; // no warning expected here

  private final Accessor<Double> myValueAccessor;

  public Foo() {
    myValue = 0;

    myValueAccessor = new Accessor<Double>() {
      @Override
      public Double get() {
        return myValue;
      }

      @Override
      public void set(Double value) {
        myValue = value; // assignment
      }
    };
  }

  public Accessor<Double> getValueAccessor() {
    return myValueAccessor;
  }
}

class T73 {
  private int <warning descr="Field 'x' may be 'final'">x</warning>; // can be final
  int y = x = 3;
}

class T74 {
  private int x; // cannot be final as reassigned in another field initializer
  {
    x = 2;
  }
  int y = x = 3;
}
// IDEA-187493
class T75 {
  void foo() {
    new Inner().innerField = 1;
  }

  private static class Inner {
    private int innerField;

    private Inner() {innerField = 0;}
  }
}
// IDEA-193896
class T76 {
  private T76 a;
  T76(T76 other) {
    a = other;
    other.a = null;
  }
}
