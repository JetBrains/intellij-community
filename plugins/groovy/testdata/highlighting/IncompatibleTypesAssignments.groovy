class X{
  int method1(Date date) {
    <warning descr="Cannot assign 'Date' to 'int'">return date</warning>;
  }

  int method2(Date date) {
    <warning descr="Cannot assign 'Date' to 'int'">date</warning>;
  }
}

X x=<warning descr="Cannot assign 'Date' to 'X'">new Date()</warning>;
x=<warning descr="Cannot assign 'Date' to 'X'">new Date()</warning>;

class Y<T> {
  T y;
}

Y y =new Y();
y.y="abc";
String s=y.y;
print y;

int xxx = <warning descr="Cannot assign 'null' to 'int'">null</warning>
char ccc = <warning descr="Cannot assign 'null' to 'char'">null</warning>