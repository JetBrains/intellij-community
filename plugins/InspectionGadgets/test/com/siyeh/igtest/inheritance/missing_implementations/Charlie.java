package com.siyeh.igtest.inheritance.missing_implementations;

public class Charlie extends Bravo {
	@Override
	protected final void perform() {
		System.out.println("I'm here.");
	}
}
