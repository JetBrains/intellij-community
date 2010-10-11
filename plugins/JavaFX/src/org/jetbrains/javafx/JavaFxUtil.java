package org.jetbrains.javafx;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.facet.JavaFxFacet;
import org.jetbrains.javafx.lang.psi.JavaFxFile;
import org.jetbrains.javafx.lang.psi.JavaFxPackageDefinition;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxUtil {
  private JavaFxUtil() {
  }

  @Nullable
  public static String getCompilerOutputPath(@NotNull final Module module) {
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    if (compilerModuleExtension != null) {
      final VirtualFile outputPath = compilerModuleExtension.getCompilerOutputPath();
      if (outputPath != null) {
        return outputPath.getPath();
      }
    }
    return null;
  }

  @NotNull
  public static String scriptNameToClassName(final Project project, final String scriptName) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(scriptName);
    if (virtualFile != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile != null && psiFile instanceof JavaFxFile) {
        final JavaFxPackageDefinition packageDefinition = ((JavaFxFile)psiFile).getPackageDefinition();
        final String nameWithoutExtension = virtualFile.getNameWithoutExtension();
        if (packageDefinition != null) {
          final String packageName = packageDefinition.getName();
          if (!"".equals(packageName)) {
            return packageName + "." + nameWithoutExtension;
          }
        }
        return nameWithoutExtension;
      }
    }
    return scriptName;
  }

  public static JavaFxFacet addFacet(final Module module, final @Nullable Sdk sdk) {
    final ModifiableFacetModel facetModel = FacetManager.getInstance(module).createModifiableModel();
    final JavaFxFacet javaFxFacet = JavaFxFacet.FACET_TYPE
      .createFacet(module, JavaFxFacet.FACET_TYPE.getDefaultFacetName(), JavaFxFacet.FACET_TYPE.createDefaultConfiguration(), null);
    facetModel.addFacet(javaFxFacet);

    if (sdk != null) {
      javaFxFacet.getConfiguration().setJavaFxSdk(sdk);
    }
    facetModel.commit();
    return javaFxFacet;
  }

  public static VirtualFile[] getModuleSourceRoots(final Module module) {
    return ModuleRootManager.getInstance(module).getSourceRoots();
  }
}
