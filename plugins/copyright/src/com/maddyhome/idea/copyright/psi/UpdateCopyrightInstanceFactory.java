package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.maddyhome.idea.copyright.CopyrightProfile;

/**
 * @author yole
 */
public interface UpdateCopyrightInstanceFactory {
  UpdateCopyright createInstance(Project project, Module module, VirtualFile file,
        FileType base, CopyrightProfile options);  
}
