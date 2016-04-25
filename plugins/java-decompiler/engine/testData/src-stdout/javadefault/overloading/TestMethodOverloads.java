package javadefault.overloading;

public class TestMethodOverloads {
public static class Base {
	void foo() { System.out.println("Base.foo"); }
	void bar(Base b) { System.out.println("Base.bar"); }
	void foobar(SubSub ss) { System.out.println("Base.foobar"); }
	
	void test() {
		System.out.println("\nBase.test running:");
		SubSub ss = new SubSub();
		foo();
		bar(ss);
		foobar(ss);
	}
}

public static class Sub extends Base {
	void foo() { System.out.println("Sub.foo"); }
	void bar(Sub s) { System.out.println("Sub.bar"); }
	void foobar(Sub ss) { System.out.println("Sub.foobar"); }
	
	void test() {
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
	void foo() { System.out.println("SubSub.foo"); }
	void bar(SubSub s) { System.out.println("SubSub.bar"); }
	void foobar(Base ss) { System.out.println("SubSub.foobar"); }
	
	void test() {
		System.out.println("\nSubSub.test running:");
		
		SubSub ss = new SubSub();
		foo();
		bar(ss);
		System.out.println("3 times the same but different param types:");
		foobar(ss);
		//casts needed
		foobar((Sub) ss);
		foobar((Base) ss);
		
		System.out.println("\nNow cast caller with param SubSub\n");
		
		System.out.println("(calls all 3):");
		((SubSub)this).bar(ss);
		((Sub)this).bar(ss);
		((Base)this).bar(ss);
		System.out.println("(calls 3 times Base):");
		((SubSub)this).foobar(ss);
		((Sub)this).foobar(ss);
		((Base)this).foobar(ss);
		
		System.out.println("\nNow cast caller with param Sub\n");
		
		Sub s = new Sub();
		System.out.println("(calls 2*Sub, 1*Base):");
		((SubSub)this).bar(s);
		((Sub)this).bar(s);
		((Base)this).bar(s);
		System.out.println("(calls * Sub):");
		((SubSub)this).foobar(s);
		((Sub)this).foobar(s);
		//Base.foobar(s);
	}
}

static public void test() {
	new Base().test();
	new Sub().test();
	new SubSub().test();
}

static public void main(String[] args) {
	test();
}
}
