package various;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderedTestsDemo {
	@Test
	@Order(2)
	void test2() {}

	@Test
	@Order(1)
	void test1() {}

	@Test
	@Order(4)
	void test4() {}
}

