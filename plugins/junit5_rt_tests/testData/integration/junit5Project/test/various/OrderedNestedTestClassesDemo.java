package various;

import org.junit.jupiter.api.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class OrderedNestedTestClassesDemo {
	@Nested
	@DisplayName("Named nested class (SecondClass)")
	@Order(2)
	class SecondTests {
		@Test
		void test2() {}
	}

	@Nested
	@Order(1)
	class FirstTests {
		@Test
		void test1() {}
	}

	@Test
	void test0() {}
}

