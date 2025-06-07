package ru.adelf.idea.dotenv.tests.usages;

import org.junit.Test;

public class RubyUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.rb"));
    }

    @Test
    public void testUsages() {
        assertUsagesContains("RUBY_TEST");
    }
}
