// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.kotlin.idea.KotlinFileType;

import java.util.Set;

/**
 * @deprecated Migrate to 'org.jetbrains.kotlin.base.util.ProjectStructureUtils'.
 */
@Deprecated
public class KotlinFileTypeFactoryUtils {
    public final static String[] KOTLIN_EXTENSIONS = new String[] { "kt", "kts" };
    public final static Set<FileType> KOTLIN_FILE_TYPES_SET = Set.of(KotlinFileType.INSTANCE);
}
