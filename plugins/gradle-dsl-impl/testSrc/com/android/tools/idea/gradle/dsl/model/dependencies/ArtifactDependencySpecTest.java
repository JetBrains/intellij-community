/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.dependencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link ArtifactDependencySpecImpl}.
 */
public class ArtifactDependencySpecTest {
  private ArtifactDependencySpecImpl myDependency;

  @Before
  public void setUp() {
    myDependency = new ArtifactDependencySpecImpl("name", "group", "version");
  }

  @Test
  public void testCreate1() {
    myDependency = ArtifactDependencySpecImpl.create("group:name:version:classifier@extension");
    assertNotNull(myDependency);
    assertEquals("group", myDependency.getGroup());
    assertEquals("name", myDependency.getName());
    assertEquals("version", myDependency.getVersion());
    assertEquals("classifier", myDependency.getClassifier());
    assertEquals("extension", myDependency.getExtension());
  }

  @Test
  public void testCreate2() {
    myDependency = ArtifactDependencySpecImpl.create("group:name:version@extension");
    assertNotNull(myDependency);
    assertEquals("group", myDependency.getGroup());
    assertEquals("name", myDependency.getName());
    assertEquals("version", myDependency.getVersion());
    assertNull(myDependency.getClassifier());
    assertEquals("extension", myDependency.getExtension());
  }

  @Test
  public void testCreate3() {
    myDependency = ArtifactDependencySpecImpl.create("group:name:version@extension");
    assertNotNull(myDependency);
    assertEquals("group", myDependency.getGroup());
    assertEquals("name", myDependency.getName());
    assertEquals("version", myDependency.getVersion());
    assertNull(myDependency.getClassifier());
    assertEquals("extension", myDependency.getExtension());
  }

  @Test
  public void testCreate4() {
    myDependency = ArtifactDependencySpecImpl.create("com.google.javascript:closure-compiler:v20151216");
    assertNotNull(myDependency);
    assertEquals("com.google.javascript", myDependency.getGroup());
    assertEquals("closure-compiler", myDependency.getName());
    assertEquals("v20151216", myDependency.getVersion());
  }

  @Test
  public void testGetCompactNotationWithoutClassifierAndExtension() {
    assertEquals("group:name:version", myDependency.compactNotation());
  }

  @Test
  public void testGetCompactNotationWithoutExtension() {
    myDependency.setClassifier("classifier");
    assertEquals("group:name:version:classifier", myDependency.compactNotation());
  }

  @Test
  public void testGetCompactNotationWithoutClassifier() {
    myDependency.setExtension("ext");
    assertEquals("group:name:version@ext", myDependency.compactNotation());
  }

  @Test
  public void testGetCompactNotation() {
    myDependency.setClassifier("classifier");
    myDependency.setExtension("ext");
    assertEquals("group:name:version:classifier@ext", myDependency.compactNotation());
  }

  @Test
  public void testEqualsFalse() {
    myDependency = ArtifactDependencySpecImpl.create("org.gradle.test.classifiers:service:1.0.0-alpha4:jdk15@jar");
    ArtifactDependencySpec theirDependency =
      ArtifactDependencySpecImpl.create("org.gradle.test.classifiers:service:1.0.0-beta1:jdk15@jar");
    assertFalse(myDependency.equals(theirDependency));
  }

  @Test
  public void testEqualsTrue() {
    myDependency = ArtifactDependencySpecImpl.create("org.gradle.test.classifiers:service:1.0.0-alpha4:jdk15@jar");
    ArtifactDependencySpec theirDependency =
      ArtifactDependencySpecImpl.create("org.gradle.test.classifiers:service:1.0.0-alpha4:jdk15@jar");
    assertTrue(myDependency.equals(theirDependency));
  }

  @Test
  public void testEqualsIgnoreVersionFalse() {
    myDependency = ArtifactDependencySpecImpl.create("org.gradle.test.classifiers:service:1.0.0-alpha4:jdk15@jar");
    ArtifactDependencySpec theirDependency =
      ArtifactDependencySpecImpl.create("org.gradle.test.classifiers:service:1.0.0-alpha4:jdk16@jar");
    assertFalse(myDependency.equalsIgnoreVersion(theirDependency));
  }

  @Test
  public void testeEqualsIgnoreVersionTrue() {
    myDependency = ArtifactDependencySpecImpl.create("org.gradle.test.classifiers:service:1.0.0-alpha4:jdk15@jar");
    ArtifactDependencySpec theirDependency =
      ArtifactDependencySpecImpl.create("org.gradle.test.classifiers:service:1.0.0-beta1:jdk15@jar");
    assertTrue(myDependency.equalsIgnoreVersion(theirDependency));
  }
}