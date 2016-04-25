package javadefault.overloading;

public class TestMethodOverloadsStatic {
public static class Base {
	static void foo() { System.out.println("Base.foo"); }
	static void bar(Base b) { System.out.println("Base.bar"); }
	static void foobar(SubSub ss) { System.out.println("Base.foobar"); }
	
	static void test() {
		System.out.println("\nBase.test running:");
		SubSub ss = new SubSub();
		foo();
		bar(ss);
		foobar(ss);
	}
}

public static class Sub extends Base {
	static void foo() { System.out.println("Sub.foo"); }
	static void bar(Sub s) { System.out.println("Sub.bar"); }
	static void foobar(Sub ss) { System.out.println("Sub.foobar"); }
	
	static void test() {
		System.out.println("\nSub.test running:");
		SubSub ss = new SubSub();
		foo();
		System.out.println("2 times the same but different param types:");
		bar(ss);
		bar((Base) ss);
		System.out.println("2 times the same but different param types:");
		foobar(ss);
		foobar((Sub) ss);
	}
}

public static class SubSub extends Sub {
	static void foo() { System.out.println("SubSub.foo"); }
	static void bar(SubSub s) { System.out.println("SubSub.bar"); }
	static void foobar(Base ss) { System.out.println("SubSub.foobar"); }
	
	static void test() {
		System.out.println("\nSubSub.test running:");
		
		SubSub ss = new SubSub();
		foo();
		bar(ss);
		System.out.println("3 times the same but different param types:");
		foobar(ss);
		//casts needed
		foobar((Sub) ss);
		foobar((Base) ss);
		
		System.out.println("\nNow call fully qualified with param SubSub\n");
		
		System.out.println("again but now fully qualified (calls all 3):");
		SubSub.bar(ss);
		Sub.bar(ss);
		Base.bar(ss);
		System.out.println("again but now fully qualified (calls 3 times Base):");
		SubSub.foobar(ss);
		Sub.foobar(ss);
		Base.foobar(ss);
		
		System.out.println("\nNow call fully qualified with param Sub\n");
		
		Sub s = new Sub();
		System.out.println("again but now fully qualified (calls 2*Sub, 1*Base):");
		SubSub.bar(s);
		Sub.bar(s);
		Base.bar(s);
		System.out.println("again but now fully qualified (calls * Sub):");
		SubSub.foobar(s);
		Sub.foobar(s);
		//Base.foobar(s);
	}
}

public static void test() {
	Base.test();
	Sub.test();
	SubSub.test();
}

public static void main(String[] args) {
	test();
}
}
