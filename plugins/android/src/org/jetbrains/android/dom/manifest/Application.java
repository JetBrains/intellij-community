package org.jetbrains.android.dom.manifest;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.ExtendClass;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.ResourceType;
import org.jetbrains.android.dom.converters.AndroidBooleanValueConverter;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

import java.util.List;

/**
 * @author yole
 */
public interface Application extends ManifestElement {

  List<Activity> getActivities();

  Activity addActivity();

  List<Provider> getProviders();

  Provider addProvider();

  List<Receiver> getReceivers();

  Receiver addReceiver();

  List<Service> getServices();

  Service addService();

  @Convert(AndroidBooleanValueConverter.class)
  AndroidAttributeValue<String> getDebuggable();

  @Convert(PackageClassConverter.class)
  @ExtendClass("android.app.Activity")
  AndroidAttributeValue<PsiClass> getManageSpaceActivity();

  @Convert(ResourceReferenceConverter.class)
  @ResourceType("string")
  AndroidAttributeValue<ResourceValue> getLabel();

  List<UsesLibrary> getUsesLibrarys();
}
