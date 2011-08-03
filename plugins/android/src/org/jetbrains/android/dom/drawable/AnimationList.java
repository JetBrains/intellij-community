package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.DefinesXml;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
@DefinesXml
public interface AnimationList extends DrawableDomElement {
  List<AnimationListItem> getItems();
}
