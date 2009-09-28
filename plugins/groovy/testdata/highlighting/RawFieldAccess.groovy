class X<T>{
  T field;
}
X x=new X();
x.field="abc";
String s=<warning descr="Cannot assign 'Object' to 'String'">x.field</warning>;