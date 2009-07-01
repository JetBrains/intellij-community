class X<T>{
  T field;
}
X x=new X();
x.field="abc";
String s=<warning descr="Cannot assign 'java.lang.Object' to 'java.lang.String'">x.field</warning>;