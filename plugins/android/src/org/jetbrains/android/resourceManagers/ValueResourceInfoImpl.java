package org.jetbrains.android.resourceManagers;

import com.android.resources.ResourceType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class ValueResourceInfoImpl extends ValueResourceInfoBase {
  private final Module myModule;
  private final int myOffset;

  ValueResourceInfoImpl(@NotNull String name, @NotNull ResourceType type, @NotNull VirtualFile file, @NotNull Module module, int offset) {
    super(name, type, file);
    myModule = module;
    myOffset = offset;
  }

  @Override
  public XmlAttributeValue computeXmlElement() {
    final ResourceElement resDomElement = computeDomElement();
    return resDomElement != null ? resDomElement.getName().getXmlAttributeValue() : null;
  }

  @Nullable
  public ResourceElement computeDomElement() {
    final Project project = myModule.getProject();
    final PsiFile file = PsiManager.getInstance(project).findFile(myFile);

    if (!(file instanceof XmlFile)) {
      return null;
    }
    final XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(file, myOffset, XmlTag.class, true);

    if (tag == null) {
      return null;
    }
    final DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);
    if (!(domElement instanceof ResourceElement)) {
      return null;
    }
    final String resType = domElement instanceof Item
                           ? ((Item)domElement).getType().getStringValue()
                           : AndroidCommonUtils.getResourceTypeByTagName(tag.getName());

    if (!myType.getName().equals(resType)) {
      return null;
    }
    final ResourceElement resDomElement = (ResourceElement)domElement;
    final String resName = ((ResourceElement)domElement).getName().getStringValue();
    return myName.equals(resName) ? resDomElement : null;
  }
}
