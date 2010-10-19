package org.jetbrains.android.dom;

import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Namespace;
import org.jetbrains.android.util.AndroidUtils;

/**
 * @author yole
 */
@Namespace(AndroidUtils.NAMESPACE_KEY)
public interface AndroidAttributeValue<T> extends GenericAttributeValue<T> {
}
