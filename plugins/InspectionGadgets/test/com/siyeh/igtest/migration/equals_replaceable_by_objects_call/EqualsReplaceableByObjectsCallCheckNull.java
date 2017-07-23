class EqualsReplaceableByObjectsCall {
  void yyy(Object a, Object b) {
    boolean c = <warning descr="'(a != null) && a.equals(b)' replaceable by 'Objects.equals()' expression">(a != null) && a.equals(b)</warning>;
    boolean d = <warning descr="'(a != b) && (a == null || !a.equals(b))' replaceable by 'Objects.equals()' expression">(a != b) && (a == null || !a.equals(b))</warning>;
    boolean e = <warning descr="'((a) == (b)) || ((a) != (null) && (a).equals((b)))' replaceable by 'Objects.equals()' expression">((a) == (b)) || ((a) != (null) && (a).equals((b)))</warning>;
  }

  void ignoreNullityCheck(Object a, Object b) {
    boolean c = a.equals(b);
  }

  void bar(T x, T y, T z) {
    boolean b = !x.s.equals(y.s);
    boolean c = <warning descr="'x.s != null && x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s != null && x.s.equals(y.s)</warning>;
    boolean d = y.s != null && x.s.equals(y.s);
    boolean e = <warning descr="'x.s == null || !x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s == null || !x.s.equals(y.s)</warning>;
    boolean f = <warning descr="'x.s != y.s && (x.s == null || !x.s.equals(y.s))' replaceable by 'Objects.equals()' expression">x.s != y.s && (x.s == null || !x.s.equals(y.s))</warning>;
    boolean g = x.s != y.s || (<warning descr="'x.s == null || !x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s == null || !x.s.equals(y.s)</warning>);
    boolean h = x.s != y.s && (z.s == null || !x.s.equals(y.s));
  }

  void baz(T x, T y) {
    boolean b = x.copy().equals(y.copy());
    boolean c = <warning descr="'x != null && x.equals(y.copy())' replaceable by 'Objects.equals()' expression">x != null &&  x.equals(y.copy())</warning>;
    boolean d = <warning descr="'x == null || !x.equals(y.copy())' replaceable by 'Objects.equals()' expression">x == null || !x.equals(y.copy())</warning>;
    boolean e = x.copy() != null && x.copy().equals(y);
    boolean f = x.copy() == null || !x.copy().equals(y);
    boolean g = x.copy().s != null && x.copy().s.equals(y.s);
    boolean h = x.copy().s == null || !x.copy().s.equals(y.s);
    boolean i = x.s == y.copy().s || <warning descr="'(x).s != null && (x.s).equals(y.copy().s)' replaceable by 'Objects.equals()' expression">(x).s != null &&  (x.s).equals(y.copy().s)</warning>;
    boolean j = x.s != y.copy().s && (<warning descr="'(x).s == null || !(x.s).equals(y.copy().s)' replaceable by 'Objects.equals()' expression">(x).s == null || !(x.s).equals(y.copy().s)</warning>);
  }

  void arr(T[] a, T[] b, int i) {
    boolean c = <warning descr="'a[i] != null && a[i].equals(b[i])' replaceable by 'Objects.equals()' expression">a[i] != null && a[i].equals(b[i])</warning>;
    boolean d = <warning descr="'a[i] == null ? b[i] == null : a[i].equals(b[i])' replaceable by 'Objects.equals()' expression">a[i] == null ? b[i] == null : a[i].equals(b[i])</warning>;
    boolean e = a[i++] != null && a[i++].equals(b[i++]);
    boolean f = a[--i] != null && a[--i].equals(b[--i]);
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
    return A.b.equals(s);
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

  static class Ternary {
    String s;
    boolean eq1(Ternary t) {
      return <warning descr="'s == null ? t.s == null : s.equals(t.s)' replaceable by 'Objects.equals()' expression">s == null ? t.s == null : s.equals(t.s)</warning>;
    }
    boolean eq2(Ternary t) {
      return (<warning descr="'(s != null) ? (s.equals(t.s)) : (t.s == null)' replaceable by 'Objects.equals()' expression">(s != null) ? (s.equals(t.s)) : (t.s == null)</warning>);
    }
    boolean ne1(Ternary t) {
      return <warning descr="'s == null ? t.s != null : !s.equals(t.s)' replaceable by 'Objects.equals()' expression">s == null ? t.s != null : !s.equals(t.s)</warning>;
    }
    boolean ne2(Ternary t) {
      return <warning descr="'(s != null) ? (!s.equals(t.s)) : !(t.s == null)' replaceable by 'Objects.equals()' expression">(s != null) ? (!s.equals(t.s)) : !(t.s == null)</warning>;
    }
    boolean notMatches1(Ternary t) {
      return s == null ? t.s != null : s.equals(t.s);
    }
    boolean notMatches2(Ternary t) {
      return s != null ? t.s != null : s.equals(t.s);
    }
    boolean notMatches3(Ternary t) {
      return ((s != null) ? !(s.equals(t.s)) : (t.s == null));
    }
    boolean notMatches4(Ternary t) {
      return s == null ? t.s == null : !s.equals(t.s);
    }
    boolean notMatches5(Ternary t) {
      return s != null ? s.equals(t.s) : t.s != null;
    }
    boolean notMatches6(Ternary t) {
      return s == null ? !s.equals(t.s) : t.s != null;
    }
    boolean notMatches7(Ternary t) {
      return s == null ? t.s == null : t.s.equals(s);
    }
    boolean notMatches8(Ternary t) {
      return (s != null) ? (t.s.equals(s)) : (t.s == null);
    }
  }
}