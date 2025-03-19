// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.K2MemberVisibilityCanBePrivateInspection;
import org.jetbrains.kotlin.idea.k2.inspections.tests.AbstractK2LocalInspectionTest;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.TestMetadataUtil;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(JUnit3RunnerWithInners.class)
public class K2MemberVisibilityCanBePrivateTest extends AbstractK2LocalInspectionTest {
    @java.lang.Override
    @org.jetbrains.annotations.NotNull
    public final KotlinPluginMode getPluginMode() {
        return KotlinPluginMode.K2;
    }

    @Override
    public @NotNull File getTestDataDirectory() {
        return new File(TestMetadataUtil.getTestDataPath(getClass()), "/code-insight/inspections-k2/tests/testData/memberVisibilityCanBePrivate");
    }

    @Override
    protected @NotNull String fileName() {
        return getTestName(true) + ".kt";
    }

    private void doRunTest() {
        myFixture.configureByFile(fileName());
        myFixture.enableInspections(K2MemberVisibilityCanBePrivateInspection.class);
        myFixture.checkHighlighting();
    }

    public void testAnnotation() { doRunTest(); }
    public void testCallableReferences() { doRunTest(); }
    public void testJvmFieldOnParameter() { doRunTest(); }
    public void testJvmFieldOnProperty() { doRunTest(); }
    public void testSameClassCallableReferences() { doRunTest(); }
    public void testSubClassFunctionCall() { doRunTest(); }
    public void testSubObjectFunctionCall() { doRunTest(); }
    public void testUsedInAnnotationOnContainingObject() { doRunTest(); }
    public void testSimple() { doRunTest(); }
    public void testInline() { doRunTest(); }
    public void testSimple2() { doRunTest(); }
}
