// closure without parameters
def cl1 = {}
cl1()
cl1(42)

// closure with single parameter
def cl2 = { a -> }
cl2()
cl2(42)

// closure with single primitive parameter
def cl3 = { int a -> }
cl3<warning descr="'cl3' cannot be applied to '()'">()</warning>
cl3(42)

// closure with single optional primitive parameter
def cl4 = { int a = -1 -> }
cl4()
cl4(42)

// closure with two parameters
def cl5 = { a, b -> }
cl5<warning descr="'cl5' cannot be applied to '()'">()</warning>
cl5<warning descr="'cl5' cannot be applied to '(java.lang.Integer)'">(42)</warning>
cl5(42, 43)

// closure with two parameters, one is optional
def cl6 = { a, b = 2 -> }
cl6<warning descr="'cl6' cannot be applied to '()'">()</warning>
cl6(42)
cl6(42, 43)
