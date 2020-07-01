package ru.adelf.idea.dotenv.tests.usages;

public class RubyUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.rb"));
    }

    public void testUsages() {
        assertUsagesContains("RUBY_TEST");
    }
}
