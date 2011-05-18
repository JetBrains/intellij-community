package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.Convert;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.ResourceType;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

/**
 * @author Eugene.Kudelevsky
 */
public interface AnimationListItem extends DrawableDomElement {
  @Convert(ResourceReferenceConverter.class)
  @ResourceType("drawable")
  AndroidAttributeValue<ResourceValue> getDrawable();
}
