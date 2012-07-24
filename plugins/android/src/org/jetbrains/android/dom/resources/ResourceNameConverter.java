package org.jetbrains.android.dom.resources;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class ResourceNameConverter extends Converter<String> {
  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s != null && StringUtil.isJavaIdentifier(AndroidResourceUtil.getFieldNameByResourceName(s))
           ? s : null;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return AndroidBundle.message("invalid.resource.name.error", s);
  }
}
