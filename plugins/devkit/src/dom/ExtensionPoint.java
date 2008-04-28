package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public interface ExtensionPoint extends DomElement {
  enum Area {
    IDEA_PROJECT,
    IDEA_MODULE,
    IDEA_APPLICATION
  }

  @NotNull
  @NameValue
  GenericAttributeValue<String> getName();

  @Attribute("qualifiedName")
  GenericAttributeValue<String> getQualifiedName();

  @NotNull
  @Convert(GlobalScopePsiClassConverter.class)
  GenericAttributeValue<PsiClass> getInterface();

  @NotNull
  @Attribute("beanClass")
  @Convert(GlobalScopePsiClassConverter.class)
  GenericAttributeValue<PsiClass> getBeanClass();

  @NotNull
  GenericAttributeValue<Area> getArea();
}
