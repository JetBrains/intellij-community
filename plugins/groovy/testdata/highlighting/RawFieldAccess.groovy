class X<T>{
  T field;
}
X x=new X();
x.field="abc";
Date s=<warning descr="Cannot assign 'Object' to 'Date'">x.field</warning>;