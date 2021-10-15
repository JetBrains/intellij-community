package ru.adelf.idea.dotenv.tests.dotenv;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import ru.adelf.idea.dotenv.inspections.DuplicateKeyInspection;
import ru.adelf.idea.dotenv.inspections.SpaceInsideNonQuotedInspection;
import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InspectionsTest extends DotEnvLightCodeInsightFixtureTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("inspections.env"));
    }

    protected String getTestDataPath() {
        return basePath + "dotenv/fixtures";
    }

    public void testDuplicateKey() {
        doInspectionTest(new DuplicateKeyInspection(), Arrays.asList("DUPLICATE_KEY=test", "DUPLICATE_KEY=test2"));
    }

    public void testSpaceInsideNonQuoted() {
        doInspectionTest(new SpaceInsideNonQuotedInspection(), Collections.singletonList("spaces without quotes"));
    }

    private void doInspectionTest(InspectionProfileEntry entry, List<String> expectedHighlightedText) {
        myFixture.enableInspections(entry);

        List<HighlightInfo> highlightInfoList = myFixture.doHighlighting();
        List<String> actualHighlightedText = new ArrayList<>();

        highlightInfoList.forEach( highlightInfo -> actualHighlightedText.add(highlightInfo.getText()));

        assertEquals(actualHighlightedText, expectedHighlightedText);
    }
}
