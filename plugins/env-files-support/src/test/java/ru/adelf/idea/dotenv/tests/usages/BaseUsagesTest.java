package ru.adelf.idea.dotenv.tests.usages;

import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

abstract class BaseUsagesTest extends DotEnvLightCodeInsightFixtureTestCase {
    protected String getTestDataPath() {
        return basePath + "usages/fixtures";
    }
}
