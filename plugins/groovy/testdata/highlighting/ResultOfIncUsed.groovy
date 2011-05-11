for (int i=0;i<10; i++) {
  def x = <warning descr="Result of increment or decrement expression used">i++</warning>
}

def x= 0

if (true) x++
print x

def foo(d) {
  <warning descr="Result of increment or decrement expression used">d++</warning>        //used in return
}