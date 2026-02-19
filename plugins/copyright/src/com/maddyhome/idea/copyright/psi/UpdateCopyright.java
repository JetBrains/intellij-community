// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.maddyhome.idea.copyright.CopyrightProfile;

/**
 * Defines how copyright should be added to the file. 
 * 
 * First called {@link #prepare()} in read action under modal progress. If progress finishes successfully, then {@link #complete()} is called in write action
 * 
 * Created at {@link UpdateCopyrightsProvider#createInstance(Project, com.intellij.openapi.module.Module, VirtualFile, FileType, CopyrightProfile)}
 */
public interface UpdateCopyright
{
    void prepare();

    void complete() throws Exception;

    VirtualFile getRoot();
}