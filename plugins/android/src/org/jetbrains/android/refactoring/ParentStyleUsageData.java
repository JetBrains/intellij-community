package org.jetbrains.android.refactoring;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class ParentStyleUsageData implements StyleUsageData {
  private final AndroidResourceReferenceBase myReference;
  private final Style myStyle;

  public ParentStyleUsageData(@NotNull Style style,
                              @NotNull AndroidResourceReferenceBase reference) {
    myReference = reference;
    myStyle = style;
  }

  @Override
  public PsiFile getFile() {
    final XmlTag tag = myStyle.getXmlTag();
    return tag != null ? tag.getContainingFile() : null;
  }

  @Override
  public void inline(@NotNull Map<AndroidAttributeInfo, String> attributeValues, @Nullable StyleRefData parentStyleRef) {
    final Map<String, String> id2Value = toId2ValueMap(attributeValues);

    for (StyleItem item : myStyle.getItems()) {
      final String name = item.getName().getStringValue();

      if (name != null) {
        id2Value.remove(name);
      }
    }

    for (Map.Entry<String, String> entry : id2Value.entrySet()) {
      final StyleItem newItem = myStyle.addItem();
      newItem.getName().setStringValue(entry.getKey());
      newItem.setStringValue(entry.getValue());
    }
    final String styleName = myStyle.getName().getStringValue();
    final boolean implicitInheritance = parentStyleRef != null &&
                                        parentStyleRef.getStylePackage() == null &&
                                        styleName != null &&
                                        (styleName.startsWith(parentStyleRef.getStyleName() + ".") ||
                                         styleName.equals(parentStyleRef.getStyleName()));

    myStyle.getParentStyle().setValue(parentStyleRef != null && !implicitInheritance
                                      ? ResourceValue.referenceTo((char)0, parentStyleRef.getStylePackage(), null,
                                                                  parentStyleRef.getStyleName())
                                      : null);
  }

  private static Map<String, String> toId2ValueMap(Map<AndroidAttributeInfo, String> info2ValueMap) {
    final Map<String, String> result = new HashMap<String, String>(info2ValueMap.size());

    for (Map.Entry<AndroidAttributeInfo, String> entry : info2ValueMap.entrySet()) {
      result.put(entry.getKey().getAttributeId(), entry.getValue());
    }
    return result;
  }

  @NotNull
  @Override
  public AndroidResourceReferenceBase getReference() {
    return myReference;
  }
}
