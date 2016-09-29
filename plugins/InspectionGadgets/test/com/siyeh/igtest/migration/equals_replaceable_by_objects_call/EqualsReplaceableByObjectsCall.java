class EqualsReplaceableByObjectsCall {
  void yyy(Object a, Object b) {
    boolean c = <warning descr="'(a != null) && a.equals(b)' replaceable by 'Objects.equals()' expression">(a != null) && a.equals(b)</warning>;
    boolean d = <warning descr="'(a != b) && (a == null || !a.equals(b))' replaceable by 'Objects.equals()' expression">(a != b) && (a == null || !a.equals(b))</warning>;
    boolean e = <warning descr="'((a) == (b)) || ((a) != (null) && (a).equals((b)))' replaceable by 'Objects.equals()' expression">((a) == (b)) || ((a) != (null) && (a).equals((b)))</warning>;
  }

  void ignoreNullityCheck(Object a, Object b) {
    boolean c = <warning descr="'a.equals(b)' replaceable by 'Objects.equals()' expression">a.equals(b)</warning>;
  }

  void bar(T x, T y, T z) {
    boolean b = !<warning descr="'x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s.equals(y.s)</warning>;
    boolean c = <warning descr="'x.s != null && x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s != null && x.s.equals(y.s)</warning>;
    boolean d = y.s != null && <warning descr="'x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s.equals(y.s)</warning>;
    boolean e = <warning descr="'x.s == null || !x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s == null || !x.s.equals(y.s)</warning>;
    boolean f = <warning descr="'x.s != y.s && (x.s == null || !x.s.equals(y.s))' replaceable by 'Objects.equals()' expression">x.s != y.s && (x.s == null || !x.s.equals(y.s))</warning>;
    boolean g = x.s != y.s || (<warning descr="'x.s == null || !x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s == null || !x.s.equals(y.s)</warning>);
    boolean h = x.s != y.s && (z.s == null || !<warning descr="'x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s.equals(y.s)</warning>);
  }

  void baz(T x, T y) {
    boolean b = <warning descr="'x.copy().equals(y.copy())' replaceable by 'Objects.equals()' expression">x.copy().equals(y.copy())</warning>;
    boolean c = <warning descr="'x != null && x.equals(y.copy())' replaceable by 'Objects.equals()' expression">x != null &&  x.equals(y.copy())</warning>;
    boolean d = <warning descr="'x == null || !x.equals(y.copy())' replaceable by 'Objects.equals()' expression">x == null || !x.equals(y.copy())</warning>;
    boolean e = x.copy() != null && <warning descr="'x.copy().equals(y)' replaceable by 'Objects.equals()' expression">x.copy().equals(y)</warning>;
    boolean f = x.copy() == null || !<warning descr="'x.copy().equals(y)' replaceable by 'Objects.equals()' expression">x.copy().equals(y)</warning>;
    boolean g = x.copy().s != null && <warning descr="'x.copy().s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.copy().s.equals(y.s)</warning>;
    boolean h = x.copy().s == null || !<warning descr="'x.copy().s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.copy().s.equals(y.s)</warning>;
    boolean i = x.s == y.copy().s || <warning descr="'(x).s != null && (x.s).equals(y.copy().s)' replaceable by 'Objects.equals()' expression">(x).s != null &&  (x.s).equals(y.copy().s)</warning>;
    boolean j = x.s != y.copy().s && (<warning descr="'(x).s == null || !(x.s).equals(y.copy().s)' replaceable by 'Objects.equals()' expression">(x).s == null || !(x.s).equals(y.copy().s)</warning>);
  }

  static class T {
    String s;
    T copy() { T t = new T(); t.s = s; return t; }
  }

  static class X extends T {
    public boolean equals(Object o) {
      return super.equals(o);
    }

    boolean same(T t) {
      return this == t || this != null && this.equals(t);
    }

    boolean different(T t) {
      return <warning descr="'t != this && (t == null || !t.equals(this))' replaceable by 'Objects.equals()' expression">t != this && (t == null || !t.equals(this))</warning>;
    }
  }

  static class A {
    static String b;
    static class B {
      static String c;
    }
  }
  static boolean ab1(String s) {
    return <warning descr="'A.b.equals(s)' replaceable by 'Objects.equals()' expression">A.b.equals(s)</warning>;
  }
  static boolean ab2(String s) {
    return <warning descr="'A.b != null && A.b.equals(s)' replaceable by 'Objects.equals()' expression">A.b != null && A.b.equals(s)</warning>;
  }
  static boolean ab3(String s) {
    return <warning descr="'A.b == s || A.b != null && A.b.equals(s)' replaceable by 'Objects.equals()' expression">A.b == s || A.b != null && A.b.equals(s)</warning>;
  }
  static boolean abc(String s) {
    return <warning descr="'A.B.c == s || A.B.c != null && A.B.c.equals(s)' replaceable by 'Objects.equals()' expression">A.B.c == s || A.B.c != null && A.B.c.equals(s)</warning>;
  }
}