class C<T> {
  T a;
  T getAt(int i){return a};
}

C c=new C();
c.a="abc";
String s=<warning descr="Cannot assign 'Object' to 'String'">c[0]</warning>;