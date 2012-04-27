def foo(Date d) {}

def a = unknown
foo<weak_warning descr="Cannot infer argument types">(a)</weak_warning>
foo<warning descr="'foo' in 'UnknownVarInArgList' cannot be applied to '(java.lang.Integer)'">(1)</warning>



def abc(Date d){}
def abc(int i) {}

def x = unknown2
abc<warning descr="Method call is ambiguous">(x)</warning>