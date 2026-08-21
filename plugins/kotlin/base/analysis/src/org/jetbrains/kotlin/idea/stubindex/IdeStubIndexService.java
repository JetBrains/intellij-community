// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.KtNodeTypes;
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.load.java.JvmAbi;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;
import org.jetbrains.kotlin.psi.stubs.KotlinAnnotationEntryStub;
import org.jetbrains.kotlin.psi.stubs.KotlinClassOrObjectStub;
import org.jetbrains.kotlin.psi.stubs.KotlinClassStub;
import org.jetbrains.kotlin.psi.stubs.KotlinFileStub;
import org.jetbrains.kotlin.psi.stubs.KotlinFunctionStub;
import org.jetbrains.kotlin.psi.stubs.KotlinModifierListStub;
import org.jetbrains.kotlin.psi.stubs.KotlinObjectStub;
import org.jetbrains.kotlin.psi.stubs.KotlinParameterStub;
import org.jetbrains.kotlin.psi.stubs.KotlinPropertyStub;
import org.jetbrains.kotlin.psi.stubs.KotlinScriptStub;
import org.jetbrains.kotlin.psi.stubs.KotlinStubWithFqName;
import org.jetbrains.kotlin.psi.stubs.KotlinTypeAliasStub;
import org.jetbrains.kotlin.psi.stubs.elements.StubIndexService;
import org.jetbrains.kotlin.psi.stubs.impl.KotlinFileStubImpl;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

public class IdeStubIndexService extends StubIndexService {

    @Override
    public void indexFile(@NotNull KotlinFileStub stub, @NotNull IndexSink sink) {
        FqName packageFqName = stub.getPackageFqName();

        sink.occurrence(KotlinExactPackagesIndex.NAME, packageFqName.asString());
        if (stub.isScript()) return;

        KotlinFileStubImpl fileStub = (KotlinFileStubImpl) stub;
        FqName facadeFqName = fileStub.getFacadeFqName();
        if (facadeFqName != null) {
            sink.occurrence(KotlinFileFacadeFqNameIndex.Helper.getIndexKey(), facadeFqName.asString());
            sink.occurrence(KotlinFileFacadeShortNameIndex.Helper.getIndexKey(), facadeFqName.shortName().asString());
            sink.occurrence(KotlinFileFacadeClassByPackageIndex.Helper.getIndexKey(), packageFqName.asString());
        }

        List<String> partNames = fileStub.getFacadePartSimpleNames();
        if (partNames != null) {
            for (String partName : partNames) {
                FqName multiFileClassPartFqName = packageFqName.child(Name.identifier(partName));
                sink.occurrence(KotlinMultiFileClassPartIndex.Helper.getIndexKey(), multiFileClassPartFqName.asString());
            }
        }
    }

    @Override
    public void indexClass(@NotNull KotlinClassStub stub, @NotNull IndexSink sink) {
        processNames(sink, stub.getName(), stub.getFqName());

        if (stub.isInterface()) {
            sink.occurrence(KotlinClassShortNameIndex.Helper.getIndexKey(), JvmAbi.DEFAULT_IMPLS_CLASS_NAME);
        }

        indexSuperNames(stub, sink);

        indexPrime(stub, sink);
    }

    /**
     * Indexes non-private top-level symbols or members of top-level objects and companion objects subject to this object serving as namespaces.
     */
    private static void indexPrime(KotlinStubWithFqName<?> stub, IndexSink sink) {
        String name = stub.getName();
        if (name == null) return;

        KotlinModifierListStub modifierList = getModifierListStub(stub);
        if (modifierList != null && modifierList.hasModifier(KtTokens.PRIVATE_KEYWORD)) return;
        if (modifierList != null && modifierList.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return;

        var parent = stub.getParentStub();
        boolean prime = false;
        if (parent instanceof KotlinFileStub) {
            prime = true;
        }
        else if (parent instanceof KotlinObjectStub) {
            var grand = parent.getParentStub();
            boolean primeGrand = grand instanceof KotlinClassStub && ((KotlinClassStub) grand).isTopLevel();

            prime = ((KotlinObjectStub) parent).isTopLevel() ||
                    primeGrand && ((KotlinObjectStub) parent).getPsi().isCompanion();
        }

        if (prime) {
            sink.occurrence(KotlinPrimeSymbolNameIndex.Helper.getIndexKey(), name);
        }
    }

    @Override
    public void indexObject(@NotNull KotlinObjectStub stub, @NotNull IndexSink sink) {
        String shortName = stub.getName();
        processNames(sink, shortName, stub.getFqName());

        indexSuperNames(stub, sink);

        indexPrime(stub, sink);

        if (shortName != null && !stub.isObjectLiteral() && !stub.getSuperNames().isEmpty()) {
            sink.occurrence(KotlinSubclassObjectNameIndex.Helper.getIndexKey(), shortName);
        }
    }

    private static void processNames(
            @NotNull IndexSink sink,
            String shortName,
            FqName fqName
    ) {
        if (shortName != null) {
            sink.occurrence(KotlinClassShortNameIndex.Helper.getIndexKey(), shortName);
        }

        if (fqName != null) {
            sink.occurrence(KotlinFullClassNameIndex.Helper.getIndexKey(), fqName.asString());
        }
    }

    private static void indexSuperNames(KotlinClassOrObjectStub<? extends KtClassOrObject> stub, IndexSink sink) {
        for (String superName : stub.getSuperNames()) {
            sink.occurrence(KotlinSuperClassIndex.Helper.getIndexKey(), superName);
        }

        if (!(stub instanceof KotlinClassStub)) {
            return;
        }

        KotlinModifierListStub modifierListStub = getModifierListStub(stub);
        if (modifierListStub == null) return;

        if (modifierListStub.hasModifier(KtTokens.ENUM_KEYWORD)) {
            sink.occurrence(KotlinSuperClassIndex.Helper.getIndexKey(), Enum.class.getSimpleName());
        }
        if (modifierListStub.hasModifier(KtTokens.ANNOTATION_KEYWORD)) {
            sink.occurrence(KotlinSuperClassIndex.Helper.getIndexKey(), Annotation.class.getSimpleName());
        }
    }

    private static @Nullable KotlinModifierListStub getModifierListStub(@NotNull KotlinStubWithFqName<?> stub) {
        StubElement<? extends PsiElement> stubByElementType = stub.findChildStubByElementType(KtNodeTypes.MODIFIER_LIST);
        if (stubByElementType instanceof KotlinModifierListStub modifierListStub) {
            return modifierListStub;
        }

        return null;
    }

    @Override
    public void indexFunction(@NotNull KotlinFunctionStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(KotlinFunctionShortNameIndex.Helper.getIndexKey(), name);

            if (IndexUtilsKt.isDeclaredInObject(stub)) {
                IndexUtilsKt.indexExtensionInObject(stub, sink);
            }

            KtNamedFunction ktNamedFunction = stub.getPsi();
            List<KtParameter> parameters = ktNamedFunction.getValueParameters();
            boolean injectedCandidate = false;
            parameterLoop: for (KtParameter parameter : parameters) {
                List<KtAnnotationEntry> annotationEntries = parameter.getAnnotationEntries();
                if (!annotationEntries.isEmpty()) {
                    for (KtAnnotationEntry entry : annotationEntries) {
                        Name shortName = entry.getShortName();
                        if (shortName != null && shortName.asString().equals("Language")) {
                            injectedCandidate = true;
                            break parameterLoop;
                        }
                    }
                }
            }
            if (injectedCandidate) {
                sink.occurrence(KotlinProbablyInjectedFunctionShortNameIndex.Helper.getIndexKey(), name);
            }

            indexPrime(stub, sink);
        }

        if (stub.isTopLevel()) {
            // can have special fq name in case of syntactically incorrect function with no name
            FqName fqName = stub.getFqName();
            if (fqName != null) {
                KtNamedFunction ktNamedFunction = stub.getPsi();
                if (KtPsiUtilKt.isExpectDeclaration(ktNamedFunction)) {
                    sink.occurrence(KotlinTopLevelExpectFunctionFqNameIndex.Helper.getIndexKey(), fqName.asString());
                }

                sink.occurrence(KotlinTopLevelFunctionFqnNameIndex.Helper.getIndexKey(), fqName.asString());
                IndexUtilsKt.indexTopLevelExtension(stub, sink);
            }
        }
    }

    @Override
    public void indexTypeAlias(@NotNull KotlinTypeAliasStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(KotlinTypeAliasShortNameIndex.Helper.getIndexKey(), name);
            indexPrime(stub, sink);
        }

        IndexUtilsKt.indexTypeAliasExpansion(stub, sink);

        FqName fqName = stub.getFqName();
        if (fqName != null) {
            sink.occurrence(KotlinFullTypeAliasNameIndex.Helper.getIndexKey(), fqName.asString());
        }
    }

    @Override
    public void indexProperty(@NotNull KotlinPropertyStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(KotlinPropertyShortNameIndex.Helper.getIndexKey(), name);

            if (IndexUtilsKt.isDeclaredInObject(stub)) {
                IndexUtilsKt.indexExtensionInObject(stub, sink);
            }

            indexPrime(stub, sink);
        }

        if (stub.isTopLevel()) {
            FqName fqName = stub.getFqName();
            // can have special fq name in case of syntactically incorrect property with no name
            if (fqName != null) {
                KtProperty ktProperty = stub.getPsi();
                if (KtPsiUtilKt.isExpectDeclaration(ktProperty)) {
                    sink.occurrence(KotlinTopLevelExpectPropertyFqNameIndex.Helper.getIndexKey(), fqName.asString());
                }

                sink.occurrence(KotlinTopLevelPropertyFqnNameIndex.Helper.getIndexKey(), fqName.asString());
                IndexUtilsKt.indexTopLevelExtension(stub, sink);
            }
        }
    }

    @Override
    public void indexParameter(@NotNull KotlinParameterStub stub, @NotNull IndexSink sink) {
        String name = stub.getName();
        if (name != null && stub.getHasValOrVar()) {
            sink.occurrence(KotlinPropertyShortNameIndex.Helper.getIndexKey(), name);
        }
    }

    @Override
    public void indexAnnotation(@NotNull KotlinAnnotationEntryStub stub, @NotNull IndexSink sink) {
        String name = stub.getShortName();
        if (name == null) {
            return;
        }
        sink.occurrence(KotlinAnnotationsIndex.Helper.getIndexKey(), name);

        PsiFileStub<?> fileStub = stub.getContainingFileStub();
        if (fileStub != null) {
            PsiFile file = fileStub.getPsi();
            if (file instanceof KtFile) {
                Collection<@NotNull String> shortImportedName = KotlinPsiHeuristics.unwrapImportAlias((KtFile) file, name);
                for (String importedName : shortImportedName) {
                    sink.occurrence(KotlinAnnotationsIndex.Helper.getIndexKey(), importedName);
                }
            }
        }

        IndexUtilsKt.indexJvmNameAnnotation(stub, sink);
    }

    @Override
    public void indexScript(@NotNull KotlinScriptStub stub, @NotNull IndexSink sink) {
        sink.occurrence(KotlinScriptFqnIndex.Helper.getIndexKey(), stub.getFqName().asString());
    }
}
