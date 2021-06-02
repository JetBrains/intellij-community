package ru.adelf.idea.dotenv.tests.usages;

public class JavaUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyDirectoryToProject("java", "src/java");
    }

    public void testGetEnvUsages() {
        assertUsagesContains("JAVA_GET_ENV");
    }
}
