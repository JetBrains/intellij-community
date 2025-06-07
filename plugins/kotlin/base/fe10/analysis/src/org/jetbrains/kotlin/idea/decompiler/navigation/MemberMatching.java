// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.decompiler.navigation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.builtins.StandardNames;
import org.jetbrains.kotlin.psi.*;

import java.util.List;

public final class MemberMatching {
    /* DECLARATIONS ROUGH MATCHING */
    private static @Nullable KtTypeReference getReceiverType(@NotNull KtNamedDeclaration propertyOrFunction) {
        if (propertyOrFunction instanceof KtCallableDeclaration) {
            return ((KtCallableDeclaration) propertyOrFunction).getReceiverTypeReference();
        }
        throw new IllegalArgumentException("Not a callable declaration: " + propertyOrFunction.getClass().getName());
    }

    private static @NotNull List<KtParameter> getValueParameters(@NotNull KtNamedDeclaration propertyOrFunction) {
        if (propertyOrFunction instanceof KtCallableDeclaration) {
            return ((KtCallableDeclaration) propertyOrFunction).getValueParameters();
        }
        throw new IllegalArgumentException("Not a callable declaration: " + propertyOrFunction.getClass().getName());
    }

    private static String getTypeShortName(@NotNull KtTypeReference typeReference) {
        KtTypeElement typeElement = typeReference.getTypeElement();
        assert typeElement != null;
        return typeElement.accept(new KtVisitor<String, Void>() {
            @Override
            public String visitDeclaration(@NotNull KtDeclaration declaration, Void data) {
                throw new IllegalStateException("This visitor shouldn't be invoked for " + declaration.getClass());
            }

            @Override
            public String visitUserType(@NotNull KtUserType type, Void data) {
                KtSimpleNameExpression referenceExpression = type.getReferenceExpression();
                assert referenceExpression != null;
                return referenceExpression.getReferencedName();
            }

            @Override
            public String visitFunctionType(@NotNull KtFunctionType type, Void data) {
                return StandardNames.getFunctionName(type.getParameters().size() + (type.getReceiverTypeReference() != null ? 1 : 0));
            }

            @Override
            public String visitNullableType(@NotNull KtNullableType nullableType, Void data) {
                KtTypeElement innerType = nullableType.getInnerType();
                assert innerType != null : "No inner type: " + nullableType;
                return innerType.accept(this, null);
            }

            @Override
            public String visitDynamicType(@NotNull KtDynamicType type, Void data) {
                return "dynamic";
            }
        }, null);
    }

    private static boolean typesHaveSameShortName(@NotNull KtTypeReference a, @NotNull KtTypeReference b) {
        return getTypeShortName(a).equals(getTypeShortName(b));
    }

    static boolean sameReceiverPresenceAndParametersCount(@NotNull KtNamedDeclaration a, @NotNull KtNamedDeclaration b) {
        boolean sameReceiverPresence = (getReceiverType(a) == null) == (getReceiverType(b) == null);
        boolean sameParametersCount = getValueParameters(a).size() == getValueParameters(b).size();
        return sameReceiverPresence && sameParametersCount;
    }

    static boolean receiverAndParametersShortTypesMatch(@NotNull KtNamedDeclaration a, @NotNull KtNamedDeclaration b) {
        KtTypeReference aReceiver = getReceiverType(a);
        KtTypeReference bReceiver = getReceiverType(b);
        if ((aReceiver == null) != (bReceiver == null)) {
            return false;
        }

        if (aReceiver != null && !typesHaveSameShortName(aReceiver, bReceiver)) {
            return false;
        }

        List<KtParameter> aParameters = getValueParameters(a);
        List<KtParameter> bParameters = getValueParameters(b);
        if (aParameters.size() != bParameters.size()) {
            return false;
        }
        for (int i = 0; i < aParameters.size(); i++) {
            KtTypeReference aType = aParameters.get(i).getTypeReference();
            KtTypeReference bType = bParameters.get(i).getTypeReference();

            assert aType != null;
            assert bType != null;

            if (!typesHaveSameShortName(aType, bType)) {
                return false;
            }
        }
        return true;
    }



    private MemberMatching() {
    }
}
