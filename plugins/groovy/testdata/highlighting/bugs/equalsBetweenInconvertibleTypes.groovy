class A {}
class B {}
class C extends B {}

def s = "1"
def i = 1
A a = new A()
B b = new B()
C c = new C()

a <warning descr="'==' between objects of inconvertible types 'A' and 'B'">==</warning> b
a <warning descr="'!=' between objects of inconvertible types 'A' and 'B'">!=</warning> b
a <warning descr="'===' between objects of inconvertible types 'A' and 'B'">===</warning> b
a <warning descr="'!==' between objects of inconvertible types 'A' and 'B'">!==</warning> b
b == c
c <warning descr="'==' between objects of inconvertible types 'C' and 'A'">==</warning> a

s.<warning descr="'equals()' between objects of inconvertible types 'String' and 'Integer'">equals</warning>(i)
i.<warning descr="'equals()' between objects of inconvertible types 'Integer' and 'String'">equals</warning>(s)
a.<warning descr="'equals()' between objects of inconvertible types 'A' and 'B'">equals</warning>(b)
b.equals(c)
c.<warning descr="'equals()' between objects of inconvertible types 'C' and 'A'">equals</warning>(a)

s.<warning descr="'equals()' between objects of inconvertible types 'String' and 'Integer'">equals</warning> i
i.<warning descr="'equals()' between objects of inconvertible types 'Integer' and 'String'">equals</warning> s
a.<warning descr="'equals()' between objects of inconvertible types 'A' and 'B'">equals</warning> b
b.equals c
c.<warning descr="'equals()' between objects of inconvertible types 'C' and 'A'">equals</warning> a
a.<warning descr="'equals()' between objects of inconvertible types 'A' and 'LinkedHashMap<String, B>'">equals</warning>(a: b)
a.<warning descr="'equals()' between objects of inconvertible types 'A' and 'Closure<Void>'">equals</warning> {}

def test(Number n, Character c, String s, GString gs) {
  n == n
  n == c
  n == s
  n <warning descr="'==' between objects of inconvertible types 'Number' and 'GString'">==</warning> gs
  c == n
  c == c
  c == s
  c <warning descr="'==' between objects of inconvertible types 'Character' and 'GString'">==</warning> gs
  s == n
  s == c
  s == s
  s == gs
  gs <warning descr="'==' between objects of inconvertible types 'GString' and 'Number'">==</warning> n
  gs <warning descr="'==' between objects of inconvertible types 'GString' and 'Character'">==</warning> c
  gs == s
  gs == gs
}
