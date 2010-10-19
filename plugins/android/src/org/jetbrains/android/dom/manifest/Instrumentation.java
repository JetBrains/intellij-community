package org.jetbrains.android.dom.manifest;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.converters.CompleteNonModuleClass;
import org.jetbrains.android.util.AndroidUtils;

/**
 * @author yole
 */
public interface Instrumentation extends ManifestElementWithName {
  @Attribute("name")
  @Required
  @Convert(PackageClassConverter.class)
  @CompleteNonModuleClass
  @ExtendClass(AndroidUtils.INSTRUMENTATION_RUNNER_BASE_CLASS)
  AndroidAttributeValue<PsiClass> getInstrumentationClass();

  @Required
  @Attribute("targetPackage")
  AndroidAttributeValue<String> getTargetPackage();
}