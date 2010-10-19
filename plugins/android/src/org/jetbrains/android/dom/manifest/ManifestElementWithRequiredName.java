package org.jetbrains.android.dom.manifest;

import org.jetbrains.android.dom.AndroidAttributeValue;
import com.intellij.util.xml.Required;

/**
 * @author coyote
 */
public interface ManifestElementWithRequiredName extends ManifestElementWithName {
    @Required
    AndroidAttributeValue<String> getName();
}
