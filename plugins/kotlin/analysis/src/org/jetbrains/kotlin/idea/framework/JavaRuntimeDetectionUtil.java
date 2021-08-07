// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.utils.PathUtil;

import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;

public class JavaRuntimeDetectionUtil {
    public static String getJavaRuntimeVersion(@NotNull Library library) {
        return getJavaRuntimeVersion(Arrays.asList(library.getFiles(OrderRootType.CLASSES)));
    }

    public static String getJavaRuntimeVersion(@NotNull List<VirtualFile> classesRoots) {
        VirtualFile stdJar = getRuntimeJar(classesRoots);
        if (stdJar != null) {
            return JarUtil.getJarAttribute(VfsUtilCore.virtualToIoFile(stdJar), Attributes.Name.IMPLEMENTATION_VERSION);
        }

        return null;
    }

    @Nullable
    public static VirtualFile getRuntimeJar(@NotNull List<VirtualFile> classesRoots) {
        for (VirtualFile root : classesRoots) {
            if (PathUtil.KOTLIN_RUNTIME_JAR_PATTERN.matcher(root.getName()).matches()) {
                return root;
            }
        }
        return null;
    }
}
