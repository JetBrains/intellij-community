package ru.adelf.idea.dotenv.tests.usages;

import org.junit.Test;

public class PhpunitUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyFileToProject("phpunit.xml");
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("phpunit.xml"));
    }

    @Test
    public void testUsages() {
        assertUsagesContains("APP_ENV", "CACHE_DRIVER", "SESSION_DRIVER");
    }
}
