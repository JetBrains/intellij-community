def foo(a) {}

foo()
foo(42)

// single primitive parameter
def fooInt(int a) {}

fooInt<warning descr="'fooInt' in 'SingleParameterMethodApplicability' cannot be applied to '()'">()</warning>
fooInt(42)

// single optional primitive parameter
def fooIntOptional(int a = -1) {}

fooIntOptional()
fooIntOptional(42)

// two parameters, one is optional
def fooTwoParameters(a, b = null) {}

fooTwoParameters() // https://issues.apache.org/jira/browse/GROOVY-8248
fooTwoParameters(42)
fooTwoParameters(42, 43)

