package org.jetbrains.android.resourceManagers;

import com.android.resources.ResourceType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ValueResourceInfoImpl extends ValueResourceInfoBase {
  private final Module myModule;

  ValueResourceInfoImpl(@NotNull String name, @NotNull ResourceType type, @NotNull VirtualFile file, @NotNull Module module) {
    super(name, type, file);
    myModule = module;
  }

  @Override
  public XmlAttributeValue computeXmlElement() {
    final ResourceElement element = computeDomElement();
    return element != null ? element.getName().getXmlAttributeValue() : null;
  }

  @Nullable
  public ResourceElement computeDomElement() {
    final Resources resources = AndroidUtils.loadDomElement(myModule, myFile, Resources.class);
    if (resources == null) {
      return null;
    }
    final List<ResourceElement> elements = AndroidResourceUtil.getValueResourcesFromElement(myType.getName(), resources);

    for (ResourceElement element : elements) {
      final String name = element.getName().getValue();

      if (myName.equals(name)) {
        return element;
      }
    }
    return null;
  }
}
