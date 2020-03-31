// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.execution;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

import java.util.Collection;

/**
 * ensure that javafx modules go to the module path after jdk 11 as OracleJDK doesn't have them in distribution anymore
 */
public class JavaFxRunConfigurationExtension extends RunConfigurationExtension {

  private static final String JAVAFX_GRAPHICS = "javafx.graphics";
  private static final String JAVAFX_BASE = "javafx.base";

  @Override
  public <T extends RunConfigurationBase> void updateJavaParameters(@NotNull T configuration,
                                                                    @NotNull JavaParameters params,
                                                                    RunnerSettings runnerSettings) {
    if (!(configuration instanceof ModuleRunProfile) || !(configuration instanceof CommonJavaRunConfigurationParameters)) return;

    Project project = configuration.getProject();
    if (DumbService.isDumb(project)) return;

    String runClass = ((CommonJavaRunConfigurationParameters)configuration).getRunClass();
    if (runClass != null) {
      GlobalSearchScope searchScope = ((ModuleRunProfile)configuration).getSearchScope();
      PsiClass aClass = searchScope != null ? JavaPsiFacade.getInstance(project).findClass(runClass, searchScope) : null;
      if (aClass != null && InheritanceUtil.isInheritor(aClass, JavaFxCommonNames.JAVAFX_APPLICATION_APPLICATION)) {
        JavaSdkVersion sdkVersion = JavaVersionService.getInstance().getJavaSdkVersion(aClass);
        if (sdkVersion != null &&
            sdkVersion.isAtLeast(JavaSdkVersion.JDK_11) &&
            params.getModulePath().isEmpty() &&
            JavaPsiFacade.getInstance(project).findModules(JAVAFX_GRAPHICS, searchScope).stream().noneMatch(mod -> belongsToJdk(mod))) {

          VirtualFile javaFxBase = getModuleJar(JAVAFX_BASE, project, searchScope);
          VirtualFile javafxGraphics = getModuleJar(JAVAFX_GRAPHICS, project, searchScope);
          if (javaFxBase != null && javafxGraphics != null) {
            params.getModulePath().add(javaFxBase);
            params.getModulePath().add(javafxGraphics);
            params.getVMParametersList().addParametersString("--add-modules " + JAVAFX_BASE + "," + JAVAFX_GRAPHICS);
            params.getVMParametersList().addParametersString("--add-reads " + JAVAFX_BASE + "=ALL-UNNAMED");
            params.getVMParametersList().addParametersString("--add-reads " + JAVAFX_GRAPHICS + "=ALL-UNNAMED");
          }
        }
      }
    }
  }

  private static boolean belongsToJdk(PsiJavaModule mod) {
    VirtualFile file = PsiUtilCore.getVirtualFile(mod);
    return file != null &&
           ProjectRootManager.getInstance(mod.getProject()).getFileIndex().getOrderEntriesForFile(file).stream().anyMatch(e -> e instanceof JdkOrderEntry);
  }

  private static VirtualFile getModuleJar(String moduleName, Project project, GlobalSearchScope searchScope) {
    Collection<PsiJavaModule> javaModules = JavaPsiFacade.getInstance(project).findModules(moduleName, searchScope);
    return VfsUtilCore.getVirtualFileForJar(PsiUtilCore.getVirtualFile(ContainerUtil.getFirstItem(javaModules)));
  }

  @Override
  public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
    return false;
  }
}
