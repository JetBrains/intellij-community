class X {
  def X(String s, int x = 0){}
}

X x = ['']
X y = ['', 2]
X q = <warning descr="Constructor 'X' in 'X' cannot be applied to '(java.lang.Integer)'">[2]</warning>

X z
z = ['', 2]
z = <warning descr="Constructor 'X' in 'X' cannot be applied to '(java.lang.Integer)'">[2]</warning>