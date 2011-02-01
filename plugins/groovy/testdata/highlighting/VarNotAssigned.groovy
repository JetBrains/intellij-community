def i=0;
def a, b

(a, b) = [i++, <warning descr="Variable 'a' might not be assigned">a</warning>]

println a
println b


