package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.web.WebUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.WebDirectoryElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 12/21/10
 */
public class J2EECoverageEngine extends JavaCoverageEngine {
  @Override
  public boolean isApplicableTo(@Nullable RunConfigurationBase conf) {
    return conf instanceof CommonModel && ((CommonModel)conf).isLocal();
  }

  @Override
  public boolean canHavePerTestCoverage(@Nullable RunConfigurationBase conf) {
    return false;
  }

  @NotNull
  @Override
  public Set<String> getQualifiedNames(@NotNull final PsiFile sourceFile) {
    if (getPackageName(sourceFile).length() == 0) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Set<String>>() {
        public Set<String> compute() {
          final PsiClass[] classes = ((PsiClassOwner)sourceFile).getClasses();
          if (classes.length == 1 && classes[0].getQualifiedName() == null) {
            final Set<String> result = new HashSet<String>();
            final VirtualFile virtualFile = sourceFile.getVirtualFile();
            if (virtualFile != null) {
              final WebDirectoryElement webDirectoryElement = WebUtil.findWebDirectoryByFile(virtualFile, sourceFile.getProject());
              if (webDirectoryElement != null) {
                final WebDirectoryElement parentDirectory = webDirectoryElement.getParentDirectory();
                String webRoot = parentDirectory != null ? parentDirectory.getPath() : "/";
                if (!webRoot.endsWith("/")) {
                  webRoot += "/";
                }
                result.add("org.apache.jsp" + webRoot.replace('/', '.') + virtualFile.getNameWithoutExtension() + "_" + virtualFile.getExtension());
                return result;
              }
            }
          }
          return J2EECoverageEngine.super.getQualifiedNames(sourceFile);
        }
      });
    }
    return super.getQualifiedNames(sourceFile);
  }
}
