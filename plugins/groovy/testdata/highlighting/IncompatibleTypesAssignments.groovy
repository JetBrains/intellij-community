class X{
  int method1(Date date) {
    <warning descr="Cannot return 'Date' from method returning 'int'">return</warning> date;
  }

  int method2(Date date) {
    <warning descr="Cannot return 'Date' from method returning 'int'">date</warning>;
  }
}

X <warning descr="Cannot assign 'Date' to 'X'">x</warning>=new Date();
<warning descr="Cannot assign 'Date' to 'X'">x</warning>=new Date();

class Y<T> {
  T y;
}

Y y =new Y();
y.y="abc";
String s=y.y;
print y;

int <warning descr="Cannot assign 'null' to 'int'">xxx</warning> = null
char <warning descr="Cannot assign 'null' to 'char'">ccc</warning> = null