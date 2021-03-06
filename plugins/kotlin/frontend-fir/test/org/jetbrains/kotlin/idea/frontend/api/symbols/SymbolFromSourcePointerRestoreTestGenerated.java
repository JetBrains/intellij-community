/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.symbols;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.jetbrains.kotlin.test.TestRoot;
import org.junit.runner.RunWith;

/*
 * This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("frontend-fir")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("testData/symbolPointer")
public class SymbolFromSourcePointerRestoreTestGenerated extends AbstractSymbolFromSourcePointerRestoreTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("class.kt")
    public void testClass() throws Exception {
        runTest("testData/symbolPointer/class.kt");
    }

    @TestMetadata("classPrimaryConstructor.kt")
    public void testClassPrimaryConstructor() throws Exception {
        runTest("testData/symbolPointer/classPrimaryConstructor.kt");
    }

    @TestMetadata("classSecondaryConstructors.kt")
    public void testClassSecondaryConstructors() throws Exception {
        runTest("testData/symbolPointer/classSecondaryConstructors.kt");
    }

    @TestMetadata("enum.kt")
    public void testEnum() throws Exception {
        runTest("testData/symbolPointer/enum.kt");
    }

    @TestMetadata("memberFunctions.kt")
    public void testMemberFunctions() throws Exception {
        runTest("testData/symbolPointer/memberFunctions.kt");
    }

    @TestMetadata("memberProperties.kt")
    public void testMemberProperties() throws Exception {
        runTest("testData/symbolPointer/memberProperties.kt");
    }

    @TestMetadata("topLevelFunctions.kt")
    public void testTopLevelFunctions() throws Exception {
        runTest("testData/symbolPointer/topLevelFunctions.kt");
    }

    @TestMetadata("topLevelProperties.kt")
    public void testTopLevelProperties() throws Exception {
        runTest("testData/symbolPointer/topLevelProperties.kt");
    }
}
