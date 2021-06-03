package ru.adelf.idea.dotenv.tests.usages;

public class KotlinUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyDirectoryToProject("kotlin", "src/kotlin");
    }

    public void testGetEnvUsages() {
        assertUsagesContains("KOTLIN_GET_ENV", "KOTLIN_GET_ENV2");
    }

    public void testDotEnvGetUsages() {
        assertUsagesContains("KOTLIN_DOTENV_GET", "KOTLIN_DOTENV_GET2");
    }
}
