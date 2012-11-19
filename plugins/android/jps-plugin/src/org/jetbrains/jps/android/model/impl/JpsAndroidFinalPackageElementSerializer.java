package org.jetbrains.jps.android.model.impl;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsAndroidFinalPackageElementSerializer extends JpsPackagingElementSerializer<JpsAndroidFinalPackageElement> {

  @NonNls private static final String PACKAGING_FACET_ATTRIBUTE = "facet";

  public JpsAndroidFinalPackageElementSerializer() {
    super("android-final-package", JpsAndroidFinalPackageElement.class);
  }

  @Override
  public JpsAndroidFinalPackageElement load(Element element) {
    final JpsModuleReference moduleReference = JpsFacetSerializer.createModuleReference(
      element.getAttributeValue(PACKAGING_FACET_ATTRIBUTE));
    return new JpsAndroidFinalPackageElement(moduleReference);
  }

  @Override
  public void save(JpsAndroidFinalPackageElement element, Element tag) {
    final String id = JpsFacetSerializer.getFacetId(element.getModuleReference(),
                                                    AndroidJpsUtil.ANDROID_FACET_TYPE_ID,
                                                    AndroidJpsUtil.ANDROID_FACET_NAME);
    tag.setAttribute(PACKAGING_FACET_ATTRIBUTE, id);
  }
}
