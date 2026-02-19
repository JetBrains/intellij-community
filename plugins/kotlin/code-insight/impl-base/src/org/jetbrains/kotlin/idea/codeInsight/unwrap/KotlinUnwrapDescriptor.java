// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapDescriptorBase;
import com.intellij.codeInsight.unwrap.Unwrapper;

public class KotlinUnwrapDescriptor extends UnwrapDescriptorBase {
    @Override
    protected Unwrapper[] createUnwrappers() {
        return new Unwrapper[] {
                new KotlinUnwrappers.KotlinExpressionRemover("remove.expression"),
                new KotlinUnwrappers.KotlinThenUnwrapper("unwrap.expression"),
                new KotlinUnwrappers.KotlinElseRemover("remove.else"),
                new KotlinUnwrappers.KotlinElseUnwrapper("unwrap.else"),
                new KotlinUnwrappers.KotlinLoopUnwrapper("unwrap.expression"),
                new KotlinUnwrappers.KotlinTryUnwrapper("unwrap.expression"),
                new KotlinUnwrappers.KotlinCatchUnwrapper("unwrap.expression"),
                new KotlinUnwrappers.KotlinCatchRemover("remove.expression"),
                new KotlinUnwrappers.KotlinFinallyUnwrapper("unwrap.expression"),
                new KotlinUnwrappers.KotlinFinallyRemover("remove.expression"),
                new KotlinLambdaUnwrapper("unwrap.expression"),
                new KotlinFunctionParameterUnwrapper("unwrap.parameter")
        };
    }
}
