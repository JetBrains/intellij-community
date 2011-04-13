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

package org.jetbrains.android.dom.layout;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.DefinesXml;
import org.jetbrains.android.dom.ResourceType;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.converters.ViewClassConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jul 30, 2009
 * Time: 10:50:13 PM
 * To change this template use File | Settings | File Templates.
 */
@DefinesXml
public interface LayoutViewElement extends LayoutElement {
  @Convert(ResourceReferenceConverter.class)
  @ResourceType("style")
  GenericAttributeValue<ResourceValue> getStyle();

  @Attribute("class")
  @Convert(ViewClassConverter.class)
  GenericAttributeValue<PsiClass> getViewClass();

  List<Include> getIncludes();

  List<Fragment> getFragments();
}
