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

  static class T {
    String s;
    T copy() { T t = new T(); t.s = s; return t; }
  }
}