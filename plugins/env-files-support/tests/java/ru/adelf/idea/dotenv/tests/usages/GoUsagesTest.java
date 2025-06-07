package ru.adelf.idea.dotenv.tests.usages;

import org.junit.Test;

public class GoUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.go"));
    }

    @Test
    public void testUsages() {
        assertUsagesContains("GO_TEST", "GO_TEST2");
    }
}
