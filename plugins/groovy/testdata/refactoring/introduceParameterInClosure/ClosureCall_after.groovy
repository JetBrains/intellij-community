Closure clos = { String anObject -> println anObject }
if ([1, 2, 3].length()>1) {clos.call("test")}