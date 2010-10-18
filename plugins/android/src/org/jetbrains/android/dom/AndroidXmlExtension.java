package org.jetbrains.android.dom;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.DefaultXmlExtension;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlExtension extends DefaultXmlExtension {
  @Override
  public TagNameReference createTagNameReference(ASTNode nameElement, boolean startTagFlag) {
    return new AndroidClassTagNameReference(nameElement, startTagFlag);
  }

  @Override
  public boolean isAvailable(final PsiFile file) {
    if (file instanceof XmlFile) {
      if (AndroidFacet.getInstance(file) == null) {
        return false;
      }
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return ResourceManager.isInResourceSubdirectory(file, null);
        }
      });
    }
    return false;
  }
}
