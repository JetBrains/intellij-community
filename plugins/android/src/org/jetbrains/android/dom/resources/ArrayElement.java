package org.jetbrains.android.dom.resources;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.android.dom.converters.QuietResourceReferenceConverter;

/**
 * @author Eugene.Kudelevsky
 */
@Convert(QuietResourceReferenceConverter.class)
public interface ArrayElement extends GenericDomValue {
}
