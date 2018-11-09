/*
 * Copyright 2011-2018 Bas Leijdekkers
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
package com.siyeh.ig.ui;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.OrderedSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;

/**
 * Always assign instances of this class to a final field to prevent an InstantiationException
 * from DefaultJDOMExternalizer.
 * <p>
 * The constructor of this class takes parameters. This means an instance of this class
 * cannot be constructed by {@link com.intellij.openapi.util.DefaultJDOMExternalizer}.
 * If instances of this class are assigned to a final field, DefaultJDOMExternalizer will
 * omit the construction of a new instance and prevent the problem.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class ExternalizableStringSet extends OrderedSet<String>
  implements JDOMExternalizable {

  private static final String ITEM = "item";
  private static final String VALUE = "value";

  private final String[] defaultValues;

  /**
   * note: declare ExternalizableStringSet fields as <b>final</b>!<br>
   * note: when it's not empty, a reference to the defaultValues array is retained by this set!
   */
  public ExternalizableStringSet(@NonNls String... defaultValues) {
    this.defaultValues = defaultValues.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : defaultValues;
    Collections.addAll(this, defaultValues);
  }

  public boolean hasDefaultValues() {
    if (size() != defaultValues.length) {
      return false;
    }
    for (String defaultValue : defaultValues) {
      if (!contains(defaultValue)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    boolean dataFound = false;
    for (Element item : element.getChildren(ITEM)) {
      if (!dataFound) {
        clear(); // remove default values
        dataFound = true;
      }
      add(StringUtil.unescapeXml(item.getAttributeValue(VALUE)));
    }
  }

  @Override
  public void writeExternal(Element element) {
    if (hasDefaultValues()) {
      return;
    }

    for (String value : this) {
      if (value != null) {
        final Element item = new Element(ITEM);
        item.setAttribute(VALUE, StringUtil.escapeXml(value));
        element.addContent(item);
      }
    }
  }

  /**
   * Write this ExternalizableStringSet to the specified element, with the specified name, if it has non-default values.
   * @param element  the element to write to.
   * @param name  the name of the option.
   */
  public void writeSettings(Element element, String name) {
    if (hasDefaultValues()) {
      return;
    }

    final Element optionElement = new Element("option").setAttribute("name", name);
    final Element valueElement = new Element("value");
    writeExternal(valueElement);
    element.addContent(optionElement.addContent(valueElement));
  }
}
