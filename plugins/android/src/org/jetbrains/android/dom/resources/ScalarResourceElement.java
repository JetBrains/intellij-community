package org.jetbrains.android.dom.resources;

import com.intellij.util.xml.Convert;
import org.jetbrains.android.dom.converters.QuietResourceReferenceConverter;

/**
 * @author Eugene.Kudelevsky
 */
@Convert(QuietResourceReferenceConverter.class)
public interface ScalarResourceElement extends ResourceElement {
}
