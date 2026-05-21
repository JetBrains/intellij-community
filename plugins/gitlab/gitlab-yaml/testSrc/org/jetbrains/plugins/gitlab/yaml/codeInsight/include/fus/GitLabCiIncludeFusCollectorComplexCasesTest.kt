// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.yaml.codeInsight.include.fus

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
internal class GitLabCiIncludeFusCollectorComplexCasesTest {

  @Nested
  inner class ComplicationsAreMergedWithLogicalOr {
    val project1Path = tempPathFixture()
    val project1 = projectFixture(project1Path, openAfterCreation = true)
    val module1 = project1.moduleFixture(project1Path, addPathToSourceRoot = true)
    val sourceRoot1 = module1.sourceRootFixture(pathFixture = project1Path)

    val project2Path = tempPathFixture()
    val project2 = projectFixture(project2Path, openAfterCreation = true)
    val module2 = project2.moduleFixture(project2Path, addPathToSourceRoot = true)
    val sourceRoot2 = module2.sourceRootFixture(pathFixture = project2Path)

    @Suppress("unused")
    val project1File = sourceRoot1.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - local: '/$MY_PATH/*/template.yml'
      
      build:
        script: echo "Building"
    """.trimIndent())

    @Suppress("unused")
    val project2File = sourceRoot2.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - local: '/$MY_PATH/**/template.yml'
      
      test:
        script: echo "Testing"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val result = GitLabCiIncludeApplicationMetricsCollector().performAnalysis()
      assertQuality(actualResult = result, filesAnalyzed = 2, filesFailed = 0, timeoutHappened = false)

      val stats = result.includeStats
      assertExplicitLocalFound(actualStats = stats, hasRules = false, hasEnvVar = true, hasSingleAsterisk = true, hasDoubleAsterisk = true)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class OnlyGitlabCiFilesAreProcessed {
    val projectPath = tempPathFixture()
    val project = projectFixture(projectPath, openAfterCreation = true)
    val module = project.moduleFixture(projectPath, addPathToSourceRoot = true)
    val sourceRoot = module.sourceRootFixture(pathFixture = projectPath)

    @Suppress("unused")
    val gitlabCiFile = sourceRoot.virtualFileFixture(".gitlab-ci.yml", """
      include:
        - local: '/templates/.gitlab-ci-template.yml'
      build:
        script: echo "Building"
    """.trimIndent())

    @Suppress("unused")
    val txtFile = sourceRoot.virtualFileFixture(".gitlab-ci.txt", """
      include:
        - local: '/other/.gitlab-ci-other.yml'
      test:
        script: echo "Testing"
    """.trimIndent())

    @Suppress("unused")
    val nonGitlabCiYmlFile = sourceRoot.virtualFileFixture("non-gitlab-ci-name.yml", """
      include:
        - template: 'Auto-DevOps.gitlab-ci.yml'
      deploy:
        script: echo "Deploying"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val result = GitLabCiIncludeApplicationMetricsCollector().performAnalysis()
      assertQuality(actualResult = result, filesAnalyzed = 1, filesFailed = 0, timeoutHappened = false)

      val stats = result.includeStats
      assertExplicitLocalFound(actualStats = stats, hasRules = false, hasEnvVar = false, hasSingleAsterisk = false, hasDoubleAsterisk = false)
      assertExplicitRemoteNotFound(stats)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateNotFound(stats)
      assertComponentNotFound(stats)
      assertProjectNotFound(stats)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class MultiprojectSingleFiles {
    val project1Path = tempPathFixture()
    val project1 = projectFixture(project1Path, openAfterCreation = true)
    val module1 = project1.moduleFixture(project1Path, addPathToSourceRoot = true)
    val sourceRoot1 = module1.sourceRootFixture(pathFixture = project1Path)

    val project2Path = tempPathFixture()
    val project2 = projectFixture(project2Path, openAfterCreation = true)
    val module2 = project2.moduleFixture(project2Path, addPathToSourceRoot = true)
    val sourceRoot2 = module2.sourceRootFixture(pathFixture = project2Path)

    val project3Path = tempPathFixture()
    val project3 = projectFixture(project3Path, openAfterCreation = true)
    val module3 = project3.moduleFixture(project3Path, addPathToSourceRoot = true)
    val sourceRoot3 = module3.sourceRootFixture(pathFixture = project3Path)

    @Suppress("unused")
    val project1File = sourceRoot1.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - local: '/templates/**/.gitlab-ci-template.yml'
          rules:
            - if: $CI_COMMIT_BRANCH == "main"
        - remote: 'https://example.com/ci-templates/$VERSION/template.yml'
          cache:
            key: ci-cache
      
      build:
        script: echo "Building"
    """.trimIndent())

    @Suppress("unused")
    val project2File = sourceRoot2.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - remote: 'https://gitlab.com/templates/security.yml'
        - template: 'Security/SAST.gitlab-ci.yml'
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
      
      test:
        script: echo "Testing"
    """.trimIndent())

    @Suppress("unused")
    val project3File = sourceRoot3.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - template: 'Jobs/Build.gitlab-ci.yml'
        - project: 'my-group/my-project-$ENV'
          ref: main
          file:
            - '/templates/*/.builds.yml'
            - '/templates/**/.tests.yml'
            - '/templates/**/subdir/*/.tests.yml'
          rules:
            - if: $CI_COMMIT_TAG
      
      deploy:
        script: echo "Deploying"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val result = GitLabCiIncludeApplicationMetricsCollector().performAnalysis()
      assertQuality(actualResult = result, filesAnalyzed = 3, filesFailed = 0, timeoutHappened = false)

      val stats = result.includeStats

      assertExplicitLocalFound(actualStats = stats, hasRules = true, hasEnvVar = false, hasSingleAsterisk = false, hasDoubleAsterisk = true)
      assertExplicitRemoteFound(actualStats = stats, hasRules = false, hasEnvVar = true, hasCache = true)
      assertImplicitLocalOrRemoteNotFound(stats)
      assertTemplateFound(actualStats = stats, hasRules = true, hasEnvVar = false)
      assertComponentNotFound(stats)
      assertProjectFound(actualStats = stats,
                         hasRules = true,
                         hasEnvVar = true,
                         hasRef = true,
                         hasSingleAsterisk = true,
                         hasDoubleAsterisk = true)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class MultiprojectMultifile {
    val project1Path = tempPathFixture()
    val project1 = projectFixture(project1Path, openAfterCreation = true)
    val module1 = project1.moduleFixture(project1Path, addPathToSourceRoot = true)
    val sourceRoot1 = module1.sourceRootFixture(pathFixture = project1Path)

    val project2Path = tempPathFixture()
    val project2 = projectFixture(project2Path, openAfterCreation = true)
    val module2 = project2.moduleFixture(project2Path, addPathToSourceRoot = true)
    val sourceRoot2 = module2.sourceRootFixture(pathFixture = project2Path)

    @Suppress("unused")
    val project1File1 = sourceRoot1.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - remote: 'https://$DOMAIN/template.yml'
          cache:
            key: cache-key
        - component: 'gitlab.com/org/component@1.0'
          rules:
            - if: $CI_COMMIT_BRANCH
      
      build:
        script: echo "Building"
    """.trimIndent())

    @Suppress("unused")
    val project1File2 = sourceRoot1.virtualFileFixture("custom.gitlab-ci.yml", """
      include:
        - template: 'Auto-DevOps.gitlab-ci.yml'
        - subdir/**/config.yml
      
      test:
        script: echo "Testing"
    """.trimIndent())

    @Suppress("unused")
    val project2File1 = sourceRoot2.virtualFileFixture(".gitlab-ci.main.yml", $$"""
      include:
        - local: '/ci/*.yml'
          rules:
            - if: $CI_PIPELINE_SOURCE == "push"
        - project: 'group/project'
          file: '/ci/deploy.yml'
          ref: stable
      
      deploy:
        script: echo "Deploying"
    """.trimIndent())

    @Suppress("unused")
    val project2File2 = sourceRoot2.virtualFileFixture("pipeline.gitlab-ci.yaml", """
      include:
        - https://example.com/shared.yml
      
      release:
        script: echo "Releasing"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val result = GitLabCiIncludeApplicationMetricsCollector().performAnalysis()
      assertQuality(actualResult = result, filesAnalyzed = 4, filesFailed = 0, timeoutHappened = false)

      val stats = result.includeStats
      assertExplicitLocalFound(actualStats = stats, hasRules = true, hasEnvVar = false, hasSingleAsterisk = true, hasDoubleAsterisk = false)
      assertExplicitRemoteFound(actualStats = stats, hasRules = false, hasEnvVar = true, hasCache = true)
      assertImplicitLocalOrRemoteFound(actualStats = stats, hasEnvVar = false, hasSingleAsterisk = false, hasDoubleAsterisk = true)
      assertTemplateFound(actualStats = stats, hasRules = false, hasEnvVar = false)
      assertComponentFound(actualStats = stats, hasRules = true, hasEnvVar = false)
      assertProjectFound(actualStats = stats,
                         hasRules = false,
                         hasEnvVar = false,
                         hasRef = true,
                         hasSingleAsterisk = false,
                         hasDoubleAsterisk = false)
      assertUnknownNotFound(stats)
    }
  }

  @Nested
  inner class AllIncludeTypesWithAllComplications {
    val project1Path = tempPathFixture()
    val project1 = projectFixture(project1Path, openAfterCreation = true)
    val module1 = project1.moduleFixture(project1Path, addPathToSourceRoot = true)
    val sourceRoot1 = module1.sourceRootFixture(pathFixture = project1Path)

    val project2Path = tempPathFixture()
    val project2 = projectFixture(project2Path, openAfterCreation = true)
    val module2 = project2.moduleFixture(project2Path, addPathToSourceRoot = true)
    val sourceRoot2 = module2.sourceRootFixture(pathFixture = project2Path)

    @Suppress("unused")
    val project1File1 = sourceRoot1.virtualFileFixture(".gitlab-ci.yml", $$"""
      include:
        - local: '/$VAR/*/template.yml'
          rules:
            - if: $CI_COMMIT_BRANCH
        - remote: 'https://$DOMAIN/ci.yml'
          rules:
            - if: $CI_PIPELINE_SOURCE == "merge_request_event"
          cache:
            key: remote-cache
        - template: '$TEMPLATE_NAME.yml'
          rules:
            - if: $CI_COMMIT_TAG
      
      build:
        script: echo "Building"
    """.trimIndent())

    @Suppress("unused")
    val project1File2 = sourceRoot1.virtualFileFixture("test.gitlab-ci.yml", $$"""
      include:
        - local: '/ci/**/.gitlab-ci.yml'
        - $MY_VAR/**/*.yml
        - component: '$CI_SERVER_HOST/org/comp@1.0'
          rules:
            - if: $CI_MERGE_REQUEST_ID
      
      test:
        script: echo "Testing"
    """.trimIndent())

    @Suppress("unused")
    val project2File1 = sourceRoot2.virtualFileFixture(".gitlab-ci.main.yml", $$"""
      include:
        - project: 'org/$PROJECT'
          file:
            - '/templates/*.yml'
            - '/configs/**/.gitlab-ci.yml'
          ref: main
          rules:
            - if: $CI_COMMIT_REF_NAME
        - unknown_type: 'some_value'
      
      deploy:
        script: echo "Deploying"
    """.trimIndent())

    @Suppress("unused")
    val project2File2 = sourceRoot2.virtualFileFixture("release.gitlab-ci.yaml", $$"""
      include:
        - https://$EXTERNAL_DOMAIN/release.yml
      
      release:
        script: echo "Releasing"
    """.trimIndent())

    @Test
    fun test() = runBlocking {
      val result = GitLabCiIncludeApplicationMetricsCollector().performAnalysis()
      assertQuality(actualResult = result, filesAnalyzed = 4, filesFailed = 0, timeoutHappened = false)

      val stats = result.includeStats
      assertExplicitLocalFound(actualStats = stats, hasRules = true, hasEnvVar = true, hasSingleAsterisk = true, hasDoubleAsterisk = true)
      assertExplicitRemoteFound(actualStats = stats, hasRules = true, hasEnvVar = true, hasCache = true)
      assertImplicitLocalOrRemoteFound(actualStats = stats, hasEnvVar = true, hasSingleAsterisk = true, hasDoubleAsterisk = true)
      assertTemplateFound(actualStats = stats, hasRules = true, hasEnvVar = true)
      assertComponentFound(actualStats = stats, hasRules = true, hasEnvVar = true)
      assertProjectFound(actualStats = stats,
                         hasRules = true,
                         hasEnvVar = true,
                         hasRef = true,
                         hasSingleAsterisk = true,
                         hasDoubleAsterisk = true)
      assertUnknownFound(stats)
    }
  }
}

