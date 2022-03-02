// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubs;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener;
import org.jetbrains.kotlin.idea.caches.trackers.PureKotlinCodeBlockModificationListener;
import org.jetbrains.kotlin.idea.stubindex.KotlinFileStubForIde;
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex;
import org.jetbrains.kotlin.idea.test.AstAccessControl;
import org.jetbrains.kotlin.idea.test.KotlinJdkAndMultiplatformStdlibDescriptor;
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.psi.stubs.KotlinClassStub;
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jetbrains.kotlin.idea.test.AstAccessControl.dropPsiAndTestWithControlledAccessToAst;

@RunWith(JUnit38ClassRunner.class)
public class KotlinStubsTest extends LightJavaCodeInsightFixtureTestCase {
    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return KotlinJdkAndMultiplatformStdlibDescriptor.Companion.getJDK_AND_MULTIPLATFORM_STDLIB_WITH_SOURCES();
    }

    public void testSuperclassNames() {
        PsiFile psiFile = myFixture.configureByText("foo.kt", "import java.util.ArrayList as alist\nclass C(): alist() { }");
        List<KtDeclaration> declarations = ((KtFile) psiFile).getDeclarations();
        KtClass ktClass = (KtClass) declarations.get(0);
        KotlinClassStub stub = KtStubElementTypes.CLASS.createStub(ktClass, null);
        List<String> names = stub.getSuperNames();
        assertSameElements(names, "ArrayList", "alist");
    }

    public void testClassIsTrait() {
        PsiFile psiFile = myFixture.configureByText("foo.kt", "interface Test { }");
        List<KtDeclaration> declarations = ((KtFile) psiFile).getDeclarations();
        KtClass ktClass = (KtClass) declarations.get(0);
        KotlinClassStub stub = KtStubElementTypes.CLASS.createStub(ktClass, null);
        assertEquals(true, stub.isInterface());
    }

    public void testScriptDeclaration() {
        PsiFile psiFile = myFixture.configureByText("foo.kts", "fun foo() {}");
        KtFile ktFile = (KtFile) psiFile;

        assertNull("file is parsed from AST", ktFile.getStub());
        List<KtDeclaration> astDeclarations = ktFile.getDeclarations();
        assertEquals(1, astDeclarations.size());
        assertTrue(astDeclarations.get(0) instanceof KtScript);

        dropPsiAndTestWithControlledAccessToAst(true, ktFile, getTestRootDisposable(), () -> {
            List<KtDeclaration> stubDeclarations = ktFile.getDeclarations();
            assertEquals(1, stubDeclarations.size());
            assertTrue(stubDeclarations.get(0) instanceof KtScript);

            return null;
        });
    }

    public void testUArrayJvmPackageNameInStubs() {
        Project project = myFixture.getProject();
        PsiFile file = myFixture.configureByText("foo.kts", "val q = UByteArray(1).<caret>asList()");

        KotlinFunctionShortNameIndex shortNameIndex = KotlinFunctionShortNameIndex.getInstance();
        CommonProcessors.CollectUniquesProcessor<KtNamedFunction> processor = new CommonProcessors.CollectUniquesProcessor<>();
        shortNameIndex.processElements("asULongArray", project, GlobalSearchScope.allScope(project), processor);
        Collection<KtNamedFunction> asULongArrayFunctions =
                ContainerUtil.filter(
                        processor.getResults(),
                        f -> f.getFqName().toString().equals("kotlin.collections.asULongArray") &&
                             f.getContainingFile().getVirtualFile().toString().endsWith("_UArrays.kt"));
        assertFalse(asULongArrayFunctions.isEmpty());
        for (KtNamedFunction asULongArrayFunction : asULongArrayFunctions) {
            KtFile ktFile = (KtFile) asULongArrayFunction.getContainingFile();
            AstAccessControl.INSTANCE.testWithControlledAccessToAst(
                    true, file.getVirtualFile(), getProject(), getTestRootDisposable(),
                    new Function0<>() {
                        @Override
                        public Unit invoke() {

                            KotlinFileStubForIde stub = (KotlinFileStubForIde) ktFile.getStub();
                            assertNotNull(stub);

                            assertEquals("kotlin.collections.UArraysKt", stub.getFacadeFqName().asString());
                            return Unit.INSTANCE;
                        }
                    });

            assertEquals("kotlin.collections", ktFile.getPackageFqName().asString());

        }
    }
}
