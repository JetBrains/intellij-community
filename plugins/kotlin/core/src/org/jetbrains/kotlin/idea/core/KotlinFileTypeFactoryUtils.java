// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.kotlin.idea.KotlinFileType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class KotlinFileTypeFactoryUtils {
    public final static String[] KOTLIN_EXTENSIONS = new String[] { "kt", "kts" };
    private final static FileType[] KOTLIN_FILE_TYPES = new FileType[] { KotlinFileType.INSTANCE };
    public final static Set<FileType> KOTLIN_FILE_TYPES_SET = new HashSet<>(Arrays.asList(KOTLIN_FILE_TYPES));
}
