package ru.adelf.idea.dotenv.tests.usages;

public class JsUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.js"));
    }

    public void testUsages() {
        assertUsagesContains("JS_TEST");
    }
}
