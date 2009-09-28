package com.siyeh.igtest.abstraction.weaken_type.sub;

public class NumberAdderImpl implements NumberAdder {

	public int doSomething() {
		return getNumberOne() + 1;
	}

	protected int getNumberOne() {
		return 1;
	}
}

