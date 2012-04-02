def foo(Date d) {}

def a = unknown
foo(a)
foo<warning descr="'foo' in 'UnknownVarInArgList' cannot be applied to '(java.lang.Integer)'">(1)</warning>



def abc(Date d){}
def abc(int i) {}

def x = unknown2
abc<warning descr="'abc' in 'UnknownVarInArgList' cannot be applied to '(null)'">(x)</warning>