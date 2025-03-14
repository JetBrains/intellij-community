// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.folding;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("fir/tests")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public abstract class FirFoldingTestGenerated extends AbstractFirFoldingTest {
    @RunWith(JUnit3RunnerWithInners.class)
    @TestMetadata("../../idea/tests/testData/folding/noCollapse")
    public static class NoCollapse extends AbstractFirFoldingTest {
        @java.lang.Override
        @org.jetbrains.annotations.NotNull
        public final KotlinPluginMode getPluginMode() {
            return KotlinPluginMode.K2;
        }

        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
        }

        @TestMetadata("class.kt")
        public void testClass() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/class.kt");
        }

        @TestMetadata("commentAndSingleLineFunction.kt")
        public void testCommentAndSingleLineFunction() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/commentAndSingleLineFunction.kt");
        }

        @TestMetadata("function.kt")
        public void testFunction() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/function.kt");
        }

        @TestMetadata("imports.kt")
        public void testImports() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/imports.kt");
        }

        @TestMetadata("kdocComments.kt")
        public void testKdocComments() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/kdocComments.kt");
        }

        @TestMetadata("multilineComments.kt")
        public void testMultilineComments() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/multilineComments.kt");
        }

        @TestMetadata("object.kt")
        public void testObject() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/object.kt");
        }

        @TestMetadata("oneImport.kt")
        public void testOneImport() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/oneImport.kt");
        }

        @TestMetadata("singleLineString.kt")
        public void testSingleLineString() throws Exception {
            runTest("../../idea/tests/testData/folding/noCollapse/singleLineString.kt");
        }
    }

    @RunWith(JUnit3RunnerWithInners.class)
    @TestMetadata("../../idea/tests/testData/folding/checkCollapse")
    public static class CheckCollapse extends AbstractFirFoldingTest {
        @java.lang.Override
        @org.jetbrains.annotations.NotNull
        public final KotlinPluginMode getPluginMode() {
            return KotlinPluginMode.K2;
        }

        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doSettingsFoldingTest, this, testDataFilePath);
        }

        @TestMetadata("collectionFactoryFunctions.kt")
        public void testCollectionFactoryFunctions() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/collectionFactoryFunctions.kt");
        }

        @TestMetadata("collectionFactoryFunctionsEmptyOneLine.kt")
        public void testCollectionFactoryFunctionsEmptyOneLine() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/collectionFactoryFunctionsEmptyOneLine.kt");
        }

        @TestMetadata("collectionFactoryFunctionsFewArguments.kt")
        public void testCollectionFactoryFunctionsFewArguments() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/collectionFactoryFunctionsFewArguments.kt");
        }

        @TestMetadata("customRegions.kt")
        public void testCustomRegions() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/customRegions.kt");
        }

        @TestMetadata("customRegionsNotFullBlock.kt")
        public void testCustomRegionsNotFullBlock() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/customRegionsNotFullBlock.kt");
        }

        @TestMetadata("doubleImportListsError.kt")
        public void testDoubleImportListsError() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/doubleImportListsError.kt");
        }

        @TestMetadata("functionLiteral.kt")
        public void testFunctionLiteral() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/functionLiteral.kt");
        }

        @TestMetadata("functionWithExpressionBody.kt")
        public void testFunctionWithExpressionBody() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/functionWithExpressionBody.kt");
        }

        @TestMetadata("functionWithExpressionBody2.kt")
        public void testFunctionWithExpressionBody2() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/functionWithExpressionBody2.kt");
        }

        @TestMetadata("functionWithExpressionBody3.kt")
        public void testFunctionWithExpressionBody3() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/functionWithExpressionBody3.kt");
        }

        @TestMetadata("headerKDoc.kt")
        public void testHeaderKDoc() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/headerKDoc.kt");
        }

        @TestMetadata("headerMultilineComment.kt")
        public void testHeaderMultilineComment() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/headerMultilineComment.kt");
        }

        @TestMetadata("imports.kt")
        public void testImports() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/imports.kt");
        }

        @TestMetadata("multilineCall.kt")
        public void testMultilineCall() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/multilineCall.kt");
        }

        @TestMetadata("multilineStrings.kt")
        public void testMultilineStrings() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/multilineStrings.kt");
        }

        @TestMetadata("primaryConstructor.kt")
        public void testPrimaryConstructor() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/primaryConstructor.kt");
        }

        @TestMetadata("when.kt")
        public void testWhen() throws Exception {
            runTest("../../idea/tests/testData/folding/checkCollapse/when.kt");
        }
    }
}
