package com.intellij.appengine.descriptor;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlSchemaProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class AppEngineWebSchemaProvider extends XmlSchemaProvider {
  private static final Set<String> FILE_NAMES = new HashSet<String>(Arrays.asList(AppEngineUtil.APP_ENGINE_WEB_XML_NAME,
                                                                                  AppEngineUtil.JDO_CONFIG_XML_NAME));

  @Override
  public boolean isAvailable(@NotNull XmlFile file) {
    if (!FILE_NAMES.contains(file.getName())) {
      return false;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    return AppEngineFacet.getAppEngineFacetByModule(module) != null;
  }

  @Override
  public XmlFile getSchema(@NotNull @NonNls String url, @Nullable Module module, @NotNull PsiFile baseFile) {
    if (module == null) {
      return null;
    }

    if (url.startsWith("http://appengine.google.com/ns/")) {
      for (AppEngineFacet facet : AppEngineFacet.getInstances(module)) {
        final File file = facet.getSdk().getWebSchemeFile();
        final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (virtualFile != null) {
          final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(virtualFile);
          if (psiFile instanceof XmlFile) {
            return (XmlFile)psiFile;
          }
        }
      }
    }
    else if (url.startsWith("http://java.sun.com/xml/ns/jdo/jdoconfig")) {
      final PsiPackage jdoPackage = JavaPsiFacade.getInstance(module.getProject()).findPackage("javax.jdo");
      final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
      if (jdoPackage != null) {
        for (PsiDirectory directory : jdoPackage.getDirectories(scope)) {
          final PsiFile file = directory.findFile("jdoconfig_2_3.xsd");
          if (file instanceof XmlFile) {
            return (XmlFile)file;
          }
        }
      }
    }

    return null;
  }

}
