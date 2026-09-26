// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.compiler;

import org.junit.Test;

/**
 * @author Vladislav.Soroka
 */
public class GradleJpsResourceFilteringTest extends GradleJpsCompilingTestCase {

  @Test
  public void testHeadFilter() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", """
        1 Header
        2
        3 another text
        4
        5 another text\s
        6 another text @token@ another text
        7
        8 Footer""");
    importProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          filter(HeadFilter, lines:3, skip:2)
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main");

    assertCopied("out/production/resources/dir/file.txt", """
      3 another text
      4
      5 another text\s
      """);
  }

  @Test
  public void testHeadFilter_MergedProject() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", """
        1 Header
        2
        3 another text
        4
        5 another text\s
        6 another text @token@ another text
        7
        8 Footer""");
    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          filter(HeadFilter, lines:3, skip:2)
        }"""
    );

    assertModules("project");
    compileModules("project");

    assertCopied("out/production/resources/dir/file.txt", """
      3 another text
      4
      5 another text\s
      """);
  }

  @Test
  public void testReplaceTokensFilter() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", """
        1 Header
        2
        3 #token1#another text
        4
        5 another text\s
        6 another text #token2# another text
        7
        8 Footer""");
    importProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          filter(ReplaceTokens, tokens:[token1:'<11111>', token2:'<2222>'], beginToken: '#', endToken: '#')
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main");

    assertCopied("out/production/resources/dir/file.txt", """
      1 Header
      2
      3 <11111>another text
      4
      5 another text\s
      6 another text <2222> another text
      7
      8 Footer""");
  }

  @Test
  public void testReplaceTokensFilter_MergedProject() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", """
        1 Header
        2
        3 #token1#another text
        4
        5 another text\s
        6 another text #token2# another text
        7
        8 Footer""");
    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          filter(ReplaceTokens, tokens:[token1:'<11111>', token2:'<2222>'], beginToken: '#', endToken: '#')
        }"""
    );
    assertModules("project");
    compileModules("project");

    assertCopied("out/production/resources/dir/file.txt", """
      1 Header
      2
      3 <11111>another text
      4
      5 another text\s
      6 another text <2222> another text
      7
      8 Footer""");
  }

  @Test
  public void testRenameFilter() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.txt");
    importProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          rename 'file.txt', 'file001.txt'
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main");

    assertCopied("out/production/resources/dir/file001.txt");
  }

  @Test
  public void testRenameFilter_MergedProject() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.txt");
    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          rename 'file.txt', 'file001.txt'
        }"""
    );
    assertModules("project");
    compileModules("project");

    assertCopied("out/production/resources/dir/file001.txt");
  }

  @Test
  public void testExpandPropertiesFilter() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", "some text ${myProp} another text");
    importProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        ant.project.setProperty('myProp', 'myPropValue')
        processResources {
          filter (ExpandProperties, project: ant.project)
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main");

    assertCopied("out/production/resources/dir/file.txt", "some text myPropValue another text");
  }

  @Test
  public void testExpandPropertiesFilter_MergedProject() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", "some text ${myProp} another text");
    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        ant.project.setProperty('myProp', 'myPropValue')
        processResources {
          filter (ExpandProperties, project: ant.project)
        }"""
    );
    assertModules("project");
    compileModules("project");

    assertCopied("out/production/resources/dir/file.txt", "some text myPropValue another text");
  }

  @Test
  public void testEscapeUnicodeFilter() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", "some text テキスト");
    importProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          filter (EscapeUnicode)
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main");

    assertCopied("out/production/resources/dir/file.txt", "some text \\u30c6\\u30ad\\u30b9\\u30c8");
  }

  @Test
  public void testFiltersChain() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", """
        1 Header
        2
        3 another text@token1@
        4
        5 another text\s
        6 another text @token2@ another text
        7
        8 Footer""");
    importProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          filter(HeadFilter, lines:4, skip:2)
          filter(ReplaceTokens, tokens:[token1:'<11111>', token2:'<2222>'])
          rename 'file.txt', 'file001.txt'
        }"""
    );
    assertModules("project", "project.main", "project.test");
    compileModules("project.main");

    assertCopied("out/production/resources/dir/file001.txt", """
      3 another text<11111>
      4
      5 another text\s
      6 another text <2222> another text""");
  }

  @Test
  public void testFiltersChain_MergedProject() throws Exception {
    createProjectSubFile(
      "src/main/resources/dir/file.txt", """
        1 Header
        2
        3 another text@token1@
        4
        5 another text\s
        6 another text @token2@ another text
        7
        8 Footer""");
    importProjectUsingSingeModulePerGradleProject(
      """
        apply plugin: 'java'

        import org.apache.tools.ant.filters.*
        processResources {
          filter(HeadFilter, lines:4, skip:2)
          filter(ReplaceTokens, tokens:[token1:'<11111>', token2:'<2222>'])
          rename 'file.txt', 'file001.txt'
        }"""
    );
    assertModules("project");
    compileModules("project");

    assertCopied("out/production/resources/dir/file001.txt", """
      3 another text<11111>
      4
      5 another text\s
      6 another text <2222> another text""");
  }
}