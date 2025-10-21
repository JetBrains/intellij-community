package dynamicTests;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class A implements InterfaceDynamicTestTests {
  @Override
  @Test
  public void testMethod() {}

  @TestFactory
  @Override
  public Stream<DynamicTest> classTestFactoryMethod() {
    return Stream.of(true, false, true)
      .map(value -> dynamicTest(value.toString(), () -> assertTrue(value)));
  }
}