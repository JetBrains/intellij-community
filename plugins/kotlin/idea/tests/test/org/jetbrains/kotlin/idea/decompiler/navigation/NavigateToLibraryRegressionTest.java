// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.navigation;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor;
import org.jetbrains.kotlin.idea.test.TestUtilsKt;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

@RunWith(JUnit38ClassRunner.class)
public class NavigateToLibraryRegressionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestUtilsKt.invalidateLibraryCache(getProject());
    }

    /**
     * Regression test against KT-1652
     */
    public void testRefToStdlib() {
        PsiElement navigationElement = configureAndResolve("fun foo() { <caret>println() }");
        assertSame(KotlinLanguage.INSTANCE, navigationElement.getLanguage());
    }

    /**
     * Regression test against KT-1652
     */
    public void testRefToJdk() {
        configureAndResolve("val x = java.util.HashMap<String, Int>().<caret>get(\"\")");
    }

    /**
     * Regression test against KT-1815
     */
    public void testRefToClassesWithAltSignatureAnnotations() {
        PsiElement navigationElement = configureAndResolve("fun foo(e : java.util.Map.Entry<String, String>) { e.<caret>getKey(); }");
        PsiClass expectedClass =
                JavaPsiFacade.getInstance(getProject()).findClass("java.util.Map.Entry", GlobalSearchScope.allScope(getProject()));
        assertSame(expectedClass, navigationElement.getParent());
    }


    public void testRefToFunctionWithVararg() {
        PsiElement navigationElement = configureAndResolve("val x = <caret>arrayListOf(\"\", \"\")");
        assertSame(KotlinLanguage.INSTANCE, navigationElement.getLanguage());
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance();
    }

    protected PsiElement configureAndResolve(String text) {
        myFixture.configureByText(KotlinFileType.INSTANCE, text);
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        //noinspection ConstantConditions
        return ref.resolve().getNavigationElement();
    }
}
