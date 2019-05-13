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
package com.intellij.lang.ant.dom;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.SubTagList;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomAntCall extends AntDomElement {
  
  @Attribute("target")
  @Convert(value = AntDomDefaultTargetConverter.class)
  public abstract GenericAttributeValue<TargetResolver.Result> getTarget();

  @Attribute("inheritall")
  @Convert(value = AntBooleanConverterDefaultTrue.class)
  public abstract GenericAttributeValue<Boolean> isInheritAllProperties();

  @Attribute("inheritrefs")
  @Convert(value = AntBooleanConverterDefaultFalse.class)
  public abstract GenericAttributeValue<Boolean> isInheritRefsProperties();
  
  @SubTagList("param")
  public abstract List<AntDomAntCallParam> getParams();
}
