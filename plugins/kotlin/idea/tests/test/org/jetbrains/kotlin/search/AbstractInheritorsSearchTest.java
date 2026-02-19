// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.search;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class AbstractInheritorsSearchTest extends AbstractSearcherTest {
    public void doTest(@NotNull String path) throws IOException {
        checkClassWithDirectives(path);
    }

}
