Object a = new Date()
Date d = a

def foo(Date d) {}

foo(a)

d = a

def b = a

a+=2

class X {
  def plus(X x){new X()}
}

def x = new X()

x+=new X()

x=x+x

print x

X y = true ? x : new X()