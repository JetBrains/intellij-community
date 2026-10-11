def method() {
	void <error descr="Method declaration not allowed here">nested</error>() {}    // (1)
}

def closure = {
	void <error descr="Method declaration not allowed here">nested</error>() {}    // (2)
}

if (true) def <error descr="Method declaration not allowed here">nested</error>() {}
else def <error descr="Method declaration not allowed here">nested</error>() {}

while (false) def <error descr="Method declaration not allowed here">nested</error>() {}