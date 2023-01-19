// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.checkers;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.highlighting.KotlinNameHighlightingStateUtils;
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionUtils;
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager;
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor;
import org.jetbrains.kotlin.idea.refactoring.ElementSelectionUtilsKt;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseKt;
import org.jetbrains.kotlin.idea.util.ElementKind;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid;
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode;
import org.jetbrains.kotlin.test.InTextDirectivesUtils;

import java.io.File;

import static org.jetbrains.kotlin.resolve.lazy.ResolveSession.areDescriptorsCreatedForDeclaration;

public abstract class AbstractKotlinHighlightVisitorTest extends KotlinLightCodeInsightFixtureTestCase {
    public static final String SUPPRESS_HIGHLIGHTING_DIRECTIVE = "SUPPRESS_HIGHLIGHTING";

    public void doTest(@NotNull VirtualFile file) throws Exception {
        myFixture.configureFromExistingVirtualFile(file);
        checkHighlighting(true, false, false);
        checkResolveToDescriptor();
    }

    public void doTest(@NotNull String filePath) throws Exception {
        PsiFile file = myFixture.configureByFile(fileName());
        ScriptConfigurationManager.getInstance(getProject()).getConfiguration((KtFile) file); // if it's a script, enable its highlighting (see KotlinProblemHighlightFilter)
        checkHighlighting(true, false, false);
        checkResolveToDescriptor();
    }

    public void doTest(@NotNull String... filePath) throws Exception {
        myFixture.configureByFiles(filePath);
        checkHighlighting(true, false, false);
        checkResolveToDescriptor();
    }

    public void doTestWithInfos(@NotNull String filePath) throws Exception {
        myFixture.configureByFile(fileName());

        myFixture.enableInspections(SpellCheckingInspection.class);

        KotlinNameHighlightingStateUtils.withNameHighlightingDisabled(myFixture.getProject(), () -> {
            checkHighlighting(true, true, false);
            checkResolveToDescriptor();
            return null;
        });
    }

    protected long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {
        PsiFile file = getFile();
        KtFile ktFile = file instanceof KtFile ? (KtFile) file : null;
        String text = file.getText();
        boolean suppressHighlight = InTextDirectivesUtils.isDirectiveDefined(text, "// " + SUPPRESS_HIGHLIGHTING_DIRECTIVE);
        return KotlinLightCodeInsightFixtureTestCaseKt
                .withCustomCompilerOptions(text, getProject(), getModule(), () -> {
                    try {
                        if (ktFile != null && ktFile.isScript() && myFixture instanceof JavaCodeInsightTestFixtureImpl) {
                            ((JavaCodeInsightTestFixtureImpl) myFixture).canChangeDocumentDuringHighlighting(true);
                        }
                        if (suppressHighlight && ktFile != null) {
                            ElementSelectionUtilsKt.selectElement(myFixture.getEditor(), ktFile, ElementKind.EXPRESSION,
                                                                  new Function1<PsiElement, Unit>() {
                                @Override
                                public Unit invoke(PsiElement element) {
                                    if (element instanceof KtElement) {
                                        AbstractKotlinHighlightVisitor.suppressHighlight((KtElement) element);
                                    }
                                    return Unit.INSTANCE;
                                }
                            });
                        }
                        return myFixture.checkHighlighting(checkWarnings, checkInfos, checkWeakWarnings);
                    }
                    catch (FileComparisonFailure e) {
                        throw new FileComparisonFailure(e.getMessage(), e.getExpected(), e.getActual(),
                                                        new File(e.getFilePath()).getAbsolutePath());
                    }
                });
    }

    void checkResolveToDescriptor() {
        KtFile file = (KtFile) myFixture.getFile();
        file.accept(new KtTreeVisitorVoid() {
            @Override
            public void visitDeclaration(@NotNull KtDeclaration dcl) {
                if (areDescriptorsCreatedForDeclaration(dcl)) {
                    ResolutionUtils.unsafeResolveToDescriptor(dcl, BodyResolveMode.FULL); // check for exceptions
                }
                dcl.acceptChildren(this, null);
            }
        });
    }
}
