void method1(Closure c) {
	c.call()
}

method1 {
	<error descr="Ambiguous code block">{println 'a' }</error>
}
