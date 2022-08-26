// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.test;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class LocalFileSystemUtils {
    private LocalFileSystemUtils() {
    }

    public static void refreshPath(@NotNull String path) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
        if (virtualFile != null) {
            virtualFile.getChildren();
            virtualFile.refresh(false, true);
        }
    }
}
