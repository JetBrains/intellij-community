class C<T> {
  T a;
  T getAt(int i){return a};
}

C c=new C();
c.a="abc";
Date s=<warning descr="Cannot assign 'Object' to 'Date'">c[0]</warning>;