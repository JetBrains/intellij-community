void method1(Closure c) {
	c.call()
}

method1 {
	{a -> println 'a' }
}
