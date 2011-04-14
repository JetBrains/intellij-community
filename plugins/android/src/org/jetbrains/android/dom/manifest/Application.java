/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.manifest;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
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
  @Attribute("manageSpaceActivity")
  AndroidAttributeValue<PsiClass> getManageSpaceActivity();

  @Convert(PackageClassConverter.class)
  @ExtendClass("android.app.Application")
  AndroidAttributeValue<PsiClass> getName();

  @Convert(ResourceReferenceConverter.class)
  @ResourceType("string")
  AndroidAttributeValue<ResourceValue> getLabel();

  List<UsesLibrary> getUsesLibrarys();
}
