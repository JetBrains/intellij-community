package ru.adelf.idea.dotenv.tests.usages;

public class PhpunitUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyFileToProject("phpunit.xml");
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("phpunit.xml"));
    }

    public void testUsages() {
        assertUsagesContains("APP_ENV", "CACHE_DRIVER", "SESSION_DRIVER");
    }
}
