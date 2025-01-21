package ru.adelf.idea.dotenv.tests.usages;

import org.junit.Test;

public class PhpUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.php"));
    }

    @Test
    public void testUsages() {
        assertUsagesContains("PHP_TEST", "PHP_TEST2", "PHP_TEST3", "PHP_TEST4");
    }
}
