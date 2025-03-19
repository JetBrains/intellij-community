package ru.adelf.idea.dotenv.tests.usages;

import org.junit.Test;

public class KotlinUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyDirectoryToProject("kotlin", "src/kotlin");
    }

    @Test
    public void testGetEnvUsages() {
        assertUsagesContains("KOTLIN_GET_ENV", "KOTLIN_GET_ENV2");
    }

    @Test
    public void testDotEnvGetUsages() {
        assertUsagesContains("KOTLIN_DOTENV_GET", "KOTLIN_DOTENV_GET2");
    }
}
