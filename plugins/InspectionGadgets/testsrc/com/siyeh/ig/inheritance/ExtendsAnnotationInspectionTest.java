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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ExtendsAnnotationInspectionTest extends LightJavaInspectionTestCase {

  public void testExtendsAnnotation() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ExtendsAnnotationInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package javax.enterprise.util;
import java.lang.annotation.Annotation;
public abstract class AnnotationLiteral<T extends Annotation> implements Annotation {
    protected AnnotationLiteral() {}
    public Class<? extends Annotation> annotationType() { return null; }
    @Override public boolean equals(Object other) { return false; }
    @Override public int hashCode() { return 0; }
    @Override public String toString() { return ""; }
}"""
    };
  }
}
