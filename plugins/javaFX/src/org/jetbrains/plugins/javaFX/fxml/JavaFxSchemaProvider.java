package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlSchemaProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxSchemaProvider extends XmlSchemaProvider {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxSchemaProvider.class.getName());

  @Override
  public boolean isAvailable(final @NotNull XmlFile file) {
    return JavaFxFileTypeFactory.isFxml(file);
  }

  @Nullable
  @Override
  public XmlFile getSchema(@NotNull @NonNls String url, @Nullable Module module, @NotNull PsiFile baseFile) {
    return module != null && JavaFxFileTypeFactory.isFxml(baseFile) ? getReference(module) : null;
  }

  private static XmlFile getReference(@NotNull Module module) {
    final URL resource = JavaFxSchemaProvider.class.getResource("fx.xsd");
    final VirtualFile fileByURL = VfsUtil.findFileByURL(resource);
    if (fileByURL == null) {
      LOG.error("xsd not found");
      return null;
    }

    PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(fileByURL);
    LOG.assertTrue(psiFile != null);
    return (XmlFile)psiFile.copy();
  }

}
