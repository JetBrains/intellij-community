package org.jetbrains.android.dom.layout;

import com.intellij.util.xml.SubTagList;
import org.jetbrains.android.dom.AndroidDomElement;

import java.util.List;

/**
 * @author yole
 */
public interface LayoutElement extends AndroidDomElement {
  @SubTagList("requestFocus")
  List<LayoutElement> getRequestFocuses();
}
