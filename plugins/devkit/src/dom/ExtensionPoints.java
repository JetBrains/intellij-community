package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.SubTagList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author mike
 */
public interface ExtensionPoints extends DomElement {
  @NotNull
  @SubTagList("extensionPoint")
  List<ExtensionPoint> getExtensionPoints();
  ExtensionPoint addExtensionPoint();
}
