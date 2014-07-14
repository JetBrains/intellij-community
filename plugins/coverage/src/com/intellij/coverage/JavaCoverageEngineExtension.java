package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

/**
 * User: anna
 * Date: 2/14/11
 */
public abstract class JavaCoverageEngineExtension {
  public static final ExtensionPointName<JavaCoverageEngineExtension> EP_NAME = ExtensionPointName.create("com.intellij.javaCoverageEngineExtension");

  public abstract boolean isApplicableTo(@Nullable RunConfigurationBase conf);

  public boolean suggestQualifiedName(@NotNull PsiFile sourceFile, PsiClass[] classes, Set<String> names) {
    return false;
  }

  public boolean collectOutputFiles(@NotNull final PsiFile srcFile,
                                    @Nullable final VirtualFile output,
                                    @Nullable final VirtualFile testoutput,
                                    @NotNull final CoverageSuitesBundle suite,
                                    @NotNull final Set<File> classFiles){
    return false;
  }
}
