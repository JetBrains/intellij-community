package ru.adelf.idea.dotenv.tests.completions;

import com.jetbrains.php.lang.PhpFileType;
import org.junit.Test;

public class PhpCompletionsTest extends BaseCompletionsTest {
    @Test
    public void testEnvFunction() {
        myFixture.configureByText(PhpFileType.INSTANCE, "<?php env('<caret>')");

        assertEnvCompletions();
    }

    @Test
    public void testGetEnvFunction() {
        myFixture.configureByText(PhpFileType.INSTANCE, "<?php getenv('<caret>')");

        assertEnvCompletions();
    }

    @Test
    public void testEnvArray() {
        myFixture.configureByText(PhpFileType.INSTANCE, "<?php $_ENV['<caret>'];");

        assertEnvCompletions();
    }

    @Test
    public void testServerArray() {
        myFixture.configureByText(PhpFileType.INSTANCE, "<?php $_SERVER['<caret>'];");

        assertEnvCompletions();
    }
}
