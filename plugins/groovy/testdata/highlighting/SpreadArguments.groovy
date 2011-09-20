def foo(int x, int y, String z){}

foo(1, 2, '3')
foo(1, *[2, '3'])
foo<warning descr="'foo' in 'SpreadArguments' cannot be applied to '(java.lang.Integer, java.lang.Integer, java.lang.Integer)'">(1, *[2, 3])</warning>
def list = new ArrayList()
foo(1, *list)

