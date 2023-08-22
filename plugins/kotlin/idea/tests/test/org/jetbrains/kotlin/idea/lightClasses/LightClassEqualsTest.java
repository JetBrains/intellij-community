// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.lightClasses;

import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration;
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport;
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupportBase;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase;
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

@RunWith(JUnit38ClassRunner.class)
public class LightClassEqualsTest extends KotlinLightCodeInsightFixtureTestCase {
    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance();
    }

    public void testEqualsForExplicitDeclaration() {
        myFixture.configureByText("a.kt", "class A");

        PsiClass theClass = myFixture.getJavaFacade().findClass("A");
        assertNotNull(theClass);
        assertInstanceOf(theClass, KtLightClassForSourceDeclaration.class);

        doTestEquals(((KtLightClass) theClass).getKotlinOrigin());
    }

    public void testEqualsForDecompiledClass() {
        myFixture.configureByText("a.kt", "");

        PsiClass theClass = myFixture.getJavaFacade().findClass("kotlin.Unit");
        assertNotNull(theClass);
        assertInstanceOf(theClass, KtLightClassForDecompiledDeclaration.class);

        doTestEquals(((KtLightClass) theClass).getKotlinOrigin());
    }

    static void doTestEquals(@Nullable KtClassOrObject origin) {
        assertNotNull(origin);

        KotlinAsJavaSupportBase<?> javaSupport = (KotlinAsJavaSupportBase<?>) KotlinAsJavaSupport.getInstance(origin.getProject());
        PsiClass lightClass1 = javaSupport.createLightClass(origin).getValue();
        PsiClass lightClass2 = javaSupport.createLightClass(origin).getValue();
        assertNotNull(lightClass1);
        assertNotNull(lightClass2);

        // If the same light class is returned twice, it means some caching was introduced and this test no longer makes sense.
        // Any other way of obtaining light classes should be used, which bypasses caches
        assertNotSame(lightClass1, lightClass2);

        assertEquals(lightClass1, lightClass2);
        assertEquals(lightClass1.hashCode(), lightClass2.hashCode());
    }
}
