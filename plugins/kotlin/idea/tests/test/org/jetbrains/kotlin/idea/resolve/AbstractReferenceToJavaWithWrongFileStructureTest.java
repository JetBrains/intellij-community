// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.resolve;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractReferenceToJavaWithWrongFileStructureTest extends AbstractReferenceResolveTest {
    @Override
    protected void doTest(@NotNull String path) {
        //this line intentionally creates wrong file structure for java file
        String fileName = fileName();
        myFixture.configureByFile(fileName.replace(".kt", ".java"));
        myFixture.configureByFile(fileName);
        performChecks();
    }
}
