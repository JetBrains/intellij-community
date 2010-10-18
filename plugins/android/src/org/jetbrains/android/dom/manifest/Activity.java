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
 * @author yole
 */
public interface Activity extends ApplicationComponent {
    @Attribute("name")
    @Required
    @Convert(PackageClassConverter.class)
    @ExtendClass("android.app.Activity")
    AndroidAttributeValue<PsiClass> getActivityClass();

    List<IntentFilter> getIntentFilters();

    IntentFilter addIntentFilter();
}
