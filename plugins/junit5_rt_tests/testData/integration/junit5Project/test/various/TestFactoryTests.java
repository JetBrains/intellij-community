package various;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class TestFactoryTests {

    @TestFactory
    Collection<DynamicTest> testsFromCollection() {
        return Arrays.asList(dynamicTest("1st dynamic test", () -> assertTrue(true)),
                dynamicTest("2nd dynamic test", () -> fail()));
    }

    @TestFactory
    Stream<DynamicNode> dynamicTestsWithContainers() {
        return Stream.of("A", "B").map(input -> dynamicContainer("Container " + input,
                Stream.of(dynamicTest("test 1", () -> assertNotNull(input)), dynamicContainer("container level 2",
                        Stream.of(dynamicTest("test 2", () -> {}),
                                dynamicTest("test 3", () -> {}))))));
    }
}
