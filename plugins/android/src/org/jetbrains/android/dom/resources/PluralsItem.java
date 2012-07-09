package org.jetbrains.android.dom.resources;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.converters.QuietResourceReferenceConverter;
import org.jetbrains.android.dom.converters.StaticEnumConverter;

/**
 * @author Eugene.Kudelevsky
 */
@Convert(QuietResourceReferenceConverter.class)
public interface PluralsItem extends GenericDomValue, StyledText {
  class QuantityConverter extends StaticEnumConverter {
    public QuantityConverter() {
      super("zero", "one", "two", "few", "many", "other");
    }
  }

  @Convert(QuantityConverter.class)
  @Required
  GenericAttributeValue<String> getQuantity();
}
