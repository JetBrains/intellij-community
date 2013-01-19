def foo={x, y->}

print foo.call<warning descr="'call' in 'groovy.lang.Closure<java.lang.Void>' cannot be applied to '(java.lang.Integer)'">(1)</warning>

def bar={3}
print bar.call()
print bar.call(3)
