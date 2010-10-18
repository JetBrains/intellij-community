package org.jetbrains.android.dom.manifest;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.converters.PackageClassConverter;

import java.util.List;

/**
 * @author coyote
 */
public interface ActivityAlias extends ManifestElementWithName {
  @Attribute("name")
  @Required
  @Convert(value = PackageClassConverter.class, soft = true)
  @ExtendClass("android.app.Activity")
  AndroidAttributeValue<PsiClass> getActivityClass();

  List<IntentFilter> getIntentFilters();

  IntentFilter addIntentFilter();
}
