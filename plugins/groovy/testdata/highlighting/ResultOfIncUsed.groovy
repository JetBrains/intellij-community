for (int i=0;i<10; i++) {
  def x = <warning descr="Usage of increment or decrement results">i++</warning>
}

def x= 0

if (true) x++
print x

def foo(d) {
  <warning descr="Usage of increment or decrement results">d++</warning>        //used in return
}