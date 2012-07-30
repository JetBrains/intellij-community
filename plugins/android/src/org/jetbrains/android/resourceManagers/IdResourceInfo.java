package org.jetbrains.android.resourceManagers;

import com.android.resources.ResourceType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class IdResourceInfo extends ValueResourceInfoBase {
  private final Project myProject;

  IdResourceInfo(@NotNull String name, @NotNull VirtualFile file, @NotNull Project project) {
    super(name, ResourceType.ID, file);
    myProject = project;
  }

  @Override
  public XmlAttributeValue computeXmlElement() {
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);

    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    final Ref<XmlAttributeValue> result = Ref.create();

    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttributeValue(XmlAttributeValue attributeValue) {
        if (!result.isNull()) {
          return;
        }

        if (AndroidResourceUtil.isIdDeclaration(attributeValue)) {
          final String idInAttr = AndroidResourceUtil.getResourceNameByReferenceText(attributeValue.getValue());

          if (myName.equals(idInAttr)) {
            result.set(attributeValue);
          }
        }
      }
    });
    return result.get();
  }
}
