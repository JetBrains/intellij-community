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
import com.intellij.util.xml.GenericAttributeValue;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomTimestampTask extends AntDomPropertyDefiningElement {
  // implicit properties
  public static final String DSTAMP = "DSTAMP";
  public static final String TSTAMP = "TSTAMP";
  public static final String TODAY  = "TODAY";
  
  @Attribute("prefix")
  public abstract GenericAttributeValue<String> getPrefix();

  protected List<String> getImplicitPropertyNames() {
    String prefix = getPrefix().getStringValue();
    if (prefix == null) {
      return Arrays.asList(DSTAMP, TSTAMP, TODAY);
    }
    if (!prefix.endsWith(".")) {
      prefix += ".";
    }
    return Arrays.asList(prefix + DSTAMP, prefix + TSTAMP, prefix + TODAY);
  }
  // todo: provide real values if that is really needed
  protected String calcPropertyValue(String propertyName) {
    return super.calcPropertyValue(propertyName);
  }
}
