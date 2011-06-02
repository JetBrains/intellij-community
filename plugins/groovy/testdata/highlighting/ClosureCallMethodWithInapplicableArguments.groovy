def foo={x, y->}

print <warning descr="'print' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(void)'">foo.call<warning descr="'call' in 'groovy.lang.Closure<void>' cannot be applied to '(java.lang.Integer)'">(1)</warning></warning>

def bar={3}
print bar.call()
print bar.call(3)
