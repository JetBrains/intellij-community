package testSuite;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondSuiteTest {
    @Test
    @Tag("important")
    void test1() {}
}
