/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class TypeParameterHidesVisibleTypeInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("import java.util.*;\n" +
           "abstract class TypeParameterHidesVisibleTypeInspection<List> {\n" +
           "    private Map map = new HashMap();\n" +
           "    public abstract List foo();\n" +
           "    public abstract </*Type parameter 'Set' hides visible type 'java.util.Set'*/Set/**/> Set bar();\n" +
           "    public abstract </*Type parameter 'TypeParameterHidesVisibleTypeInspection' hides visible type 'TypeParameterHidesVisibleTypeInspection'*/TypeParameterHidesVisibleTypeInspection/**/> TypeParameterHidesVisibleTypeInspection baz();\n" +
           "    public abstract <InputStream> InputStream baz3();\n" +
           "    public abstract <A> A baz2();\n" +
           "}\n");
  }

  public void testHiddenTypeParameter() {
    doTest("import java.util.*;\n" +
           " abstract class MyList<T> extends AbstractList<T> {\n" +
           "    private List<T> elements;\n" +
           "    public </*Type parameter 'T' hides type parameter 'T'*/T/**/> T[] toArray( T[] array ) {\n" +
           "        return elements.toArray( array );\n" +
           "    }\n" +
           "}\n");
  }

  public void testCanNotHideFromStaticContext() {
    doTest("class Y<T> {\n" +
           "    static class X<T> {\n" +
           "        T t;\n" +
           "    }\n" +
           "    static <T>  T go() {\n" +
           "        return null;\n" +
           "    }\n" +
           "}");
  }

  public void testCannotHideFromImplicitStaticContext() {
    doTest("interface G<T> {\n" +
           "    class I {\n" +
           "        class H<T>  {\n" +
           "             T t;\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new TypeParameterHidesVisibleTypeInspection();
  }
}
