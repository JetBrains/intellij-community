// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.psi.stubs.IndexSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics;
import org.jetbrains.kotlin.idea.base.psi.KotlinStubUtils;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.load.java.JvmAbi;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;
import org.jetbrains.kotlin.psi.stubs.*;
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes;
import org.jetbrains.kotlin.psi.stubs.elements.StubIndexService;
import org.jetbrains.kotlin.psi.stubs.impl.KotlinFileStubImpl;

import java.lang.annotation.Annotation;
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

        String partSimpleName = fileStub.getPartSimpleName();
        if (partSimpleName != null) {
            FqName partFqName = packageFqName.child(Name.identifier(partSimpleName));
            sink.occurrence(KotlinFilePartClassIndex.Helper.getIndexKey(), partFqName.asString());
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
        processNames(sink, stub.getName(), stub.getFqName(), stub.isTopLevel());

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
        processNames(sink, shortName, stub.getFqName(), stub.isTopLevel());

        indexSuperNames(stub, sink);

        indexPrime(stub, sink);

        if (shortName != null && !stub.isObjectLiteral() && !stub.getSuperNames().isEmpty()) {
            sink.occurrence(KotlinSubclassObjectNameIndex.Helper.getIndexKey(), shortName);
        }
    }

    private static void processNames(
            @NotNull IndexSink sink,
            String shortName,
            FqName fqName,
            boolean level) {
        if (shortName != null) {
            sink.occurrence(KotlinClassShortNameIndex.Helper.getIndexKey(), shortName);
        }

        if (fqName != null) {
            sink.occurrence(KotlinFullClassNameIndex.Helper.getIndexKey(), fqName.asString());

            if (level) {
                sink.occurrence(KotlinTopLevelClassByPackageIndex.Helper.getIndexKey(), fqName.parent().asString());
            }
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
        return stub.findChildStubByType(KtStubElementTypes.MODIFIER_LIST);
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
            KtTypeReference typeReference = ktNamedFunction.getTypeReference();
            if (typeReference != null && KotlinPsiHeuristics.isProbablyNothing(typeReference)) {
                sink.occurrence(KotlinProbablyNothingFunctionShortNameIndex.Helper.getIndexKey(), name);
            }

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

            if (stub.getMayHaveContract()) {
                sink.occurrence(KotlinProbablyContractedFunctionShortNameIndex.Helper.getIndexKey(), name);
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
                sink.occurrence(KotlinTopLevelFunctionByPackageIndex.Helper.getIndexKey(), fqName.parent().asString());
                IndexUtilsKt.indexTopLevelExtension(stub, sink);
            }
        }

        IndexUtilsKt.indexInternals(stub, sink);
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
            if (stub.isTopLevel()) {
                sink.occurrence(KotlinTopLevelTypeAliasFqNameIndex.Helper.getIndexKey(), fqName.asString());
                sink.occurrence(KotlinTopLevelTypeAliasByPackageIndex.Helper.getIndexKey(), fqName.parent().asString());
            }
        }

        ClassId classId = stub.getClassId();
        if (classId != null && !stub.isTopLevel()) {
            sink.occurrence(KotlinInnerTypeAliasClassIdIndex.Helper.getIndexKey(), classId.asString());
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

            KtTypeReference typeReference = stub.getPsi().getTypeReference();
            if (typeReference != null && KotlinPsiHeuristics.isProbablyNothing(typeReference)) {
                sink.occurrence(KotlinProbablyNothingPropertyShortNameIndex.Helper.getIndexKey(), name);
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
                sink.occurrence(KotlinTopLevelPropertyByPackageIndex.Helper.getIndexKey(), fqName.parent().asString());
                IndexUtilsKt.indexTopLevelExtension(stub, sink);
            }
        }

        IndexUtilsKt.indexInternals(stub, sink);
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

        KotlinFileStub fileStub = KotlinStubUtils.getContainingKotlinFileStub(stub);
        if (fileStub != null) {
            List<KotlinImportDirectiveStub> aliasImportStubs = fileStub.findImportsByAlias(name);
            for (KotlinImportDirectiveStub importStub : aliasImportStubs) {
                FqName importedFqName = importStub.getImportedFqName();
                if (importedFqName != null) {
                    sink.occurrence(KotlinAnnotationsIndex.Helper.getIndexKey(), importedFqName.shortName().asString());
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
