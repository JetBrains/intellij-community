package ru.adelf.idea.dotenv.tests.completions;

import org.junit.Test;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;

public class CompletionValuesTest extends BaseCompletionsTest {
    @Test
    public void testEnvFunction() {
        var keyValues = EnvironmentVariablesApi.getAllKeyValues(getProject());

        assertTrue(keyValues.containsKey("ENV_KEY1"));
        assertEquals("1", keyValues.get("ENV_KEY1"));

        assertTrue(keyValues.containsKey("ENV_KEY2"));
        assertEquals("2", keyValues.get("ENV_KEY2"));

        assertTrue(keyValues.containsKey("ENV_KEY_EXAMPLE"));
        assertEquals("3", keyValues.get("ENV_KEY_EXAMPLE"));
    }
}
