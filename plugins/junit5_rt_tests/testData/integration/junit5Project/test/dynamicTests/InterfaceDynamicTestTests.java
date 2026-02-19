package dynamicTests;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

interface InterfaceDynamicTestTests {

	@TestFactory
	default Stream<DynamicTest> defaultTestFactoryMethod() {
		return Stream.of(true, false, true)
			.map(value -> dynamicTest(value.toString(), () -> assertTrue(value)));
	}

	@Test
	void testMethod();

	@TestFactory
	Stream<DynamicTest> classTestFactoryMethod();
}

