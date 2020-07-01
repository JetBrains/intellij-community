package ru.adelf.idea.dotenv.tests.usages;

public class GoUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.go"));
    }

    public void testUsages() {
        assertUsagesContains("GO_TEST", "GO_TEST2");
    }
}
