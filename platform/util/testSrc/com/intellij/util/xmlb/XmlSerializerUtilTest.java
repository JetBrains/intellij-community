/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.xmlb;

import junit.framework.TestCase;

/**
 * @author nik
 */
public class XmlSerializerUtilTest extends TestCase {
  public void testCreateCopy() {
    BaseBean copy = XmlSerializerUtil.createCopy(new BaseBean("x", 1));
    assertNotNull(copy);
    assertSame(BaseBean.class, copy.getClass());
    assertEquals(1, copy.i);
    assertEquals("x", copy.s);
  }

  public void testCopyBeanOfSameClass() {
    BaseBean to = new BaseBean();
    XmlSerializerUtil.copyBean(new BaseBean("a", 1), to);
    assertEquals(1, to.i);
    assertEquals("a", to.s);
  }

  public void testCopyBeanToInheritor() {
    InheritedBean to = new InheritedBean();
    to.b = true;
    XmlSerializerUtil.copyBean(new BaseBean("a", 1), to);
    assertEquals(1, to.i);
    assertEquals("a", to.s);
    assertTrue(to.b);
  }

  public static class BaseBean {
    public String s;
    public int i;

    public BaseBean() {
    }

    public BaseBean(String s, int i) {
      this.s = s;
      this.i = i;
    }
  }

  public static class InheritedBean extends BaseBean {
    public boolean b;
  }
}
