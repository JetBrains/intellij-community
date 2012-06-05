package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.Convert;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface ListItemBase extends DrawableDomElement {
  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("drawable")
  AndroidAttributeValue<ResourceValue> getDrawable();

  List<BitmapOrNinePatchElement> getBitmaps();

  List<BitmapOrNinePatchElement> getNinePatches();

  List<Shape> getShapes();

  List<InsetOrClipOrScale> getClips();

  List<InsetOrClipOrScale> getScales();

  List<InsetOrClipOrScale> getInsets();

  List<InsetOrClipOrScale> getAnimatedRotates();

  List<InsetOrClipOrScale> getRotates();
}
