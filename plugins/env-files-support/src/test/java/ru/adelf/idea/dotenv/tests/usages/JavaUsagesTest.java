package ru.adelf.idea.dotenv.tests.usages;

import org.junit.Test;

public class JavaUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyDirectoryToProject("java", "src/java");
    }

    @Test
    public void testGetEnvUsages() {
        assertUsagesContains("JAVA_GET_ENV");
    }
}
