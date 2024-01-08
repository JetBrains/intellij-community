// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.execution.junit.FileComparisonData;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils;
import org.jetbrains.kotlin.idea.test.*;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseKt.configureRegistryAndRun;

public abstract class AbstractHighlightingTest extends KotlinLightCodeInsightFixtureTestCase {

    public static final String NO_CHECK_INFOS_PREFIX = "// NO_CHECK_INFOS";
    public static final String NO_CHECK_WEAK_WARNINGS_PREFIX = "// NO_CHECK_WEAK_WARNINGS";
    public static final String NO_CHECK_WARNINGS_PREFIX = "// NO_CHECK_WARNINGS";
    public static final String EXPECTED_DUPLICATED_HIGHLIGHTING_PREFIX = "// EXPECTED_DUPLICATED_HIGHLIGHTING";
    public static final String ALLOW_DOC_CHANGE_PREFIX = "// ALLOW_DOC_CHANGE";
    public static final String TOOL_PREFIX = "// TOOL:";

    protected void checkHighlighting(@NotNull String fileText) {
        boolean checkInfos = !InTextDirectivesUtils.isDirectiveDefined(fileText, NO_CHECK_INFOS_PREFIX);
        boolean checkWeakWarnings = !InTextDirectivesUtils.isDirectiveDefined(fileText, NO_CHECK_WEAK_WARNINGS_PREFIX);
        boolean checkWarnings = !InTextDirectivesUtils.isDirectiveDefined(fileText, NO_CHECK_WARNINGS_PREFIX);
        boolean allowDocChange = InTextDirectivesUtils.isDirectiveDefined(fileText, ALLOW_DOC_CHANGE_PREFIX);

        KotlinLightCodeInsightFixtureTestCaseKt.withCustomCompilerOptions(fileText, getProject(), getModule(), () ->
        {
            ExpectedHighlightingData data = new ExpectedHighlightingData(myFixture.getEditor().getDocument(), checkWarnings, checkWeakWarnings, checkInfos);
            if (checkInfos) data.checkSymbolNames();
            ((CodeInsightTestFixtureImpl) myFixture).canChangeDocumentDuringHighlighting(allowDocChange);
            data.init();

            return ((CodeInsightTestFixtureImpl)myFixture).collectAndCheckHighlighting(data);
        });
    }

    protected void doTest(String unused) throws Exception {
        String fileText = FileUtil.loadFile(new File(dataFilePath(fileName())), true);
        boolean expectedDuplicatedHighlighting = InTextDirectivesUtils.isDirectiveDefined(fileText, EXPECTED_DUPLICATED_HIGHLIGHTING_PREFIX);

        ConfigLibraryUtil.INSTANCE.configureLibrariesByDirective(myFixture.getModule(), fileText);
        myFixture.configureByFile(fileName());
        List<String> tools = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, TOOL_PREFIX);
        if (!tools.isEmpty()) {
            for (InspectionProfileEntry tool : InspectionTestUtil.instantiateTools(Set.copyOf(tools))) {
                myFixture.enableInspections(tool);
            }
        }

        withExpectedDuplicatedHighlighting(expectedDuplicatedHighlighting, isFirPlugin(), () -> {
            try {
                configureRegistryAndRun(fileText, () -> {
                    checkHighlighting(fileText);
                    return Unit.INSTANCE;
                });
            }
            catch (AssertionError e) {
                if (e instanceof FileComparisonData) {
                    List<HighlightInfo> highlights =
                            DaemonCodeAnalyzerImpl.getHighlights(myFixture.getDocument(myFixture.getFile()), null, myFixture.getProject());
                    String text = myFixture.getFile().getText();

                    System.out.println(TagsTestDataUtil.insertInfoTags(highlights, text));
                }
                throw e;
            }
        });
    }

    public static void withExpectedDuplicatedHighlighting(boolean expectedDuplicatedHighlighting, boolean isFirPlugin, Runnable runnable) {
        if (!expectedDuplicatedHighlighting) {
            runnable.run();
            return;
        }

        try {
            ExpectedHighlightingData.expectedDuplicatedHighlighting(runnable);
        } catch (IllegalStateException e) {
            if (isFirPlugin) {
                runnable.run();
            } else {
                throw e;
            }
        }
    }
}
