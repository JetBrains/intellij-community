package com.intellij.appengine.descriptor;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlSchemaProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author nik
 */
public class AppEngineWebSchemaProvider extends XmlSchemaProvider {
  @Override
  public boolean isAvailable(@NotNull XmlFile file) {
    if (!file.getName().equals(AppEngineUtil.APP_ENGINE_WEB_XML_NAME)) {
      return false;
    }
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return false;
    return !AppEngineFacet.getInstances(module).isEmpty();
  }

  @Override
  public XmlFile getSchema(@NotNull @NonNls String url, @Nullable Module module, @NotNull PsiFile baseFile) {
    if (!url.startsWith("http://appengine.google.com/ns/") || module == null) {
      return null;
    }

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
    return null;
  }

}
