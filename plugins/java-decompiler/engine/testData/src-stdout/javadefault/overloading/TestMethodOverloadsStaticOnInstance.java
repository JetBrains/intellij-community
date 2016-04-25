package javadefault.overloading;

public class TestMethodOverloadsStaticOnInstance {
public static class Base {
	static void foo() { System.out.println("Base.foo"); }
	static void bar(Base b) { System.out.println("Base.bar"); }
	static void foobar(SubSub ss) { System.out.println("Base.foobar"); }
	
	void test() {
		System.out.println("\nBase.test running:");
		SubSub ss = new SubSub();
		this.foo();
		this.bar(ss);
		this.foobar(ss);
	}
}

public static class Sub extends Base {
	static void foo() { System.out.println("Sub.foo"); }
	static void bar(Sub s) { System.out.println("Sub.bar"); }
	static void foobar(Sub ss) { System.out.println("Sub.foobar"); }
	
	void test() {
		System.out.println("\nSub.test running:");
		SubSub ss = new SubSub();
		this.foo();
		System.out.println("2 times the same but different param types:");
		this.bar(ss);
		this.bar((Base) ss);
		System.out.println("2 times the same but different param types:");
		this.foobar(ss);
		this.foobar((Sub) ss);
	}
}

public static class SubSub extends Sub {
	static void foo() { System.out.println("SubSub.foo"); }
	static void bar(SubSub s) { System.out.println("SubSub.bar"); }
	static void foobar(Base ss) { System.out.println("SubSub.foobar"); }
	
	void test() {
		System.out.println("\nSubSub.test running:");
		
		SubSub ss = new SubSub();
		this.foo();
		this.bar(ss);
		System.out.println("3 times the same but different param types:");
		this.foobar(ss);
		//casts needed
		this.foobar((Sub) ss);
		this.foobar((Base) ss);
		
		System.out.println("\nNow call fully qualified with param SubSub\n");
		
		System.out.println("again but now fully qualified (calls all 3):");
		((SubSub) this).bar(ss);
		((Sub) this).bar(ss);
		((Base) this).bar(ss);
		System.out.println("again but now fully qualified (calls 3 times Base):");
		((SubSub) this).foobar(ss);
		((Sub) this).foobar(ss);
		((Base) this).foobar(ss);
		
		System.out.println("\nNow call fully qualified with param Sub\n");
		
		Sub s = new Sub();
		System.out.println("again but now fully qualified (calls 2*Sub, 1*Base):");
		((SubSub) this).bar(s);
		((Sub) this).bar(s);
		((Base) this).bar(s);
		System.out.println("again but now fully qualified (calls * Sub):");
		((SubSub) this).foobar(s);
		((Sub) this).foobar(s);
		//Base.foobar(s);
	}
}

public static void test() {
	new Base().test();
	new Sub().test();
	new SubSub().test();
}

public static void main(String[] args) {
	test();
}
}
