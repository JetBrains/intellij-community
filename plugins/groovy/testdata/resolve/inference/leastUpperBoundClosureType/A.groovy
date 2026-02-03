def foo(String s ){ return null}
def bar(String s ){ return null}

def list = [A.&foo, A.&bar]
def x = list[0]
print <ref>x