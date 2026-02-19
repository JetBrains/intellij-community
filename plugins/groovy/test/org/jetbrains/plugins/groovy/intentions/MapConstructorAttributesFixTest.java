// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection;

public class MapConstructorAttributesFixTest extends GrIntentionTestCase {
  public MapConstructorAttributesFixTest() {
    super("Add required", GroovyConstructorNamedArgumentsInspection.class);
  }

  public void test_field_completion() {
    doTextTest("""
                 @groovy.transform.MapConstructor
                 class Rr {
                     private String actionType
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr(actio<caret>nType: "")
                 }
                 """, """
                 @groovy.transform.MapConstructor(includeFields = true)
                 class Rr {
                     private String actionType
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr(actio<caret>nType: "")
                 }
                 """);
  }

  public void test_two_attributes() {
    doTextTest("""
                 @groovy.transform.MapConstructor
                 class Rr {
                     private static String actionType
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr(actio<caret>nType: "")
                 }
                 """, """
                 @groovy.transform.MapConstructor(includeStatic = true, includeFields = true)
                 class Rr {
                     private static String actionType
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr(actio<caret>nType: "")
                 }
                 """);
  }

  public void test_no_fix() {
    doAntiTest("""
                 @groovy.transform.MapConstructor(includes = "a")
                 class Rr {
                     private static String actionType
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr(actio<caret>nType: "")
                 }
                 """);
  }

  public void test_no_fix_2() {
    doAntiTest("""
                 class Nn {
                   void setFoo(String s) {}
                 }
                 
                 @groovy.transform.MapConstructor
                 class Rr extends Nn {
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr(fo<caret>o: "")
                 }
                 """);
  }

  public void test_two_labels() {
    doTextTest("""
                 @groovy.transform.MapConstructor
                 class Rr {
                     private String $actionType
                     static int b
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr($ac<caret>tionType: "", b: 2)
                 }""", """
                 @groovy.transform.MapConstructor(allNames = true, includeFields = true, includeStatic = true)
                 class Rr {
                     private String $actionType
                     static int b
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr($actionType: "", b: 2)
                 }""");
  }

  public void test_raw_construction() {
    doTextTest("""
                 @groovy.transform.MapConstructor
                 class Rr {
                     private String actionType
                 }
                 
                 static void main(String[] args) {
                     def x = [actio<caret>nType: "kik"] as Rr
                     println x.actionType
                 }""", """
                 @groovy.transform.MapConstructor(includeFields = true)
                 class Rr {
                     private String actionType
                 }
                 
                 static void main(String[] args) {
                     def x = [actio<caret>nType: "kik"] as Rr
                     println x.actionType
                 }""");
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5_REAL_JDK;
  }
}
