def method() {
	void <error descr="Inner methods are not supported">nested</error>() {}    // (1)
}

def closure = {
	void <error descr="Inner methods are not supported">nested</error>() {}    // (2)
}