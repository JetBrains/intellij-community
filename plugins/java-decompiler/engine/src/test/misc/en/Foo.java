package test.misc.en;

class Foo {

	public Foo() {}
		
	public Foo(String test) {}
	
	private void foo() {
		System.out.println("qwe");
	}

	static class Bar extends Foo {
		void bar() {
			System.out.println("1");
			//((Foo)this).foo();
		}
	}

	static class Bar1 extends Bar {
		void bar() {
			super.bar();
			//System.out.println("2");
			//((Foo)this).foo();
		}
	}

	static class Bar2 extends Bar1 {
		void bar1() {
			super.bar();
		}
	}
	
	public static void main(String[] args) {
		new Bar2().bar();
	}
	
	public int testfin() {
		
		int i;
		
		try {
			System.out.println();
			i = 0;
		} finally {
			System.out.println();
		}
		
		
		return i;
	}
}
