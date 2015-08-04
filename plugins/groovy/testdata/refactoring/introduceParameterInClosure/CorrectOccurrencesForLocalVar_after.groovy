Closure clos

clos = {print "foo"}
clos()
clos.call()

clos = { String anObject -> print anObject }
clos("foo")
clos.call("foo")

clos = {print "foo"}
clos()
clos.call()
