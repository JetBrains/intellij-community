// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

@TestApplication
public class MapConstructorAttributesFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5_REAL_JDK);
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, "Add required", null, GroovyConstructorNamedArgumentsInspection.class);

  @Test
  public void test_field_completion() {
    grIntention.get().doTextTest("""
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

  @Test
  public void test_two_attributes() {
    grIntention.get().doTextTest("""
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

  @Test
  public void test_no_fix() {
    grIntention.get().doAntiTest("""
                 @groovy.transform.MapConstructor(includes = "a")
                 class Rr {
                     private static String actionType
                 }
                 
                 static void main(String[] args) {
                     def x = new Rr(actio<caret>nType: "")
                 }
                 """);
  }

  @Test
  public void test_no_fix_2() {
    grIntention.get().doAntiTest("""
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

  @Test
  public void test_two_labels() {
    grIntention.get().doTextTest("""
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

  @Test
  public void test_raw_construction() {
    grIntention.get().doTextTest("""
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
}
