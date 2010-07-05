def foo(int... i) {}
def list = [1, 2, 3]
foo<warning descr="'foo' in 'SignatureIsNotApplicableToList' cannot be applied to '([java.lang.Integer, java.lang.Integer, java.lang.Integer])'">(list)</warning>