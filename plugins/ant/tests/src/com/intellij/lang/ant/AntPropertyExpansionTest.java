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
package com.intellij.lang.ant;

import com.intellij.lang.ant.dom.PropertiesProvider;
import com.intellij.lang.ant.dom.PropertyExpander;
import com.intellij.psi.PsiElement;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class AntPropertyExpansionTest extends TestCase{


  public void testPropertyExpand() {
    //<property name="a" value="aValue"/>
    //<property name="b" value="${a}bValue"/>
    //<property name="c" value="${d}cValue"/>
    //<property name="d" value="dValue"/>
    PropertiesProvider[] providers = new PropertiesProvider[] {
      new PropertiesProviderImpl("a", "aValue"),
      new PropertiesProviderImpl("b", "${a}bValue"),
      new PropertiesProviderImpl("c", "${d}cValue"),
      new PropertiesProviderImpl("d", "dValue"),
    };

    assertEquals("abc", expand(providers, "abc"));
    assertEquals("aValue", expand(providers, "${a}"));
    assertEquals("aValuebValue", expand(providers, "${b}"));
    assertEquals("${d}cValue", expand(providers, "${c}"));
    assertEquals("dValue", expand(providers, "${d}"));
    assertEquals("${d}cValuedValue", expand(providers, "${c}${d}"));

    PropertiesProvider[] providers2 = new PropertiesProvider[] {
      new PropertiesProviderImpl("loop.me1", "prefix-${loop.me2}"),
      new PropertiesProviderImpl("loop.me2", "prefix-${loop.me1}"),
      new PropertiesProviderImpl("loop.me3", "prefix-${loop.me3}"),
      new PropertiesProviderImpl("aaa", "aaa_val_${bbb}"),
      new PropertiesProviderImpl("bbb", "bbb_val"),
      new PropertiesProviderImpl("ccc", "${aaa}_${bbb}"),
    };

    assertEquals("prefix-${loop.me2}", expand(providers2, "${loop.me1}"));
    assertEquals("prefix-prefix-${loop.me2}", expand(providers2, "${loop.me2}"));
    assertEquals("prefix-${loop.me3}", expand(providers2, "${loop.me3}"));
    assertEquals("aaa_val_${bbb}_bbb_val", expand(providers2, "${ccc}"));
  }

  private static String expand(PropertiesProvider[] providers, String str) {
    PropertyExpander expander = new PropertyExpander(str);
    if (expander.hasPropertiesToExpand()) {
      for (PropertiesProvider provider : providers) {
        expander.acceptProvider(provider);
        if (!expander.hasPropertiesToExpand()) {
          break;
        }
      }
    }
    return expander.getResult();
  }

  private static final class PropertiesProviderImpl implements PropertiesProvider{
    private final Map<String, String> myMap;

    PropertiesProviderImpl(String name, String value) {
      this(Collections.singletonMap(name, value));
    }

    PropertiesProviderImpl(Map<String, String> map) {
      myMap = map;
    }

    @Override
    @NotNull
    public Iterator<String> getNamesIterator() {
      return myMap.keySet().iterator();
    }

    @Override
    public String getPropertyValue(String propertyName) {
      return myMap.get(propertyName);
    }

    @Override
    public PsiElement getNavigationElement(String propertyName) {
      return null;
    }
  }
}
