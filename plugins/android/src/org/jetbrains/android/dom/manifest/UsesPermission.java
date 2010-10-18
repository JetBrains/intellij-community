package org.jetbrains.android.dom.manifest;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.LookupClass;
import org.jetbrains.android.dom.LookupPrefix;
import org.jetbrains.android.dom.converters.ConstantFieldConverter;

/**
 * @author yole
 */
public interface UsesPermission extends ManifestElementWithName {
    @Attribute("name")
    @Required
    @Convert(ConstantFieldConverter.class)
    @LookupClass("android.Manifest.permission")
    @LookupPrefix("android.permission")
    AndroidAttributeValue<String> getName();
}
