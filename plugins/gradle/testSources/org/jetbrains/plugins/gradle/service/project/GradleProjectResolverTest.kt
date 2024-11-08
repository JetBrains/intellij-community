// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.junit.jupiter.api.Test
import java.nio.file.Path

class GradleProjectResolverTest : GradleProjectResolverTestCase() {

  @Test
  fun `test content root merging`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val moduleNode = createModuleNode().apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to moduleNode))

    assertContentRoots(moduleNode, projectPath.resolve("src/main"), projectPath.resolve("src/test"))
    assertSourceRoots(moduleNode, projectPath.resolve("src/main")) {
      sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java"))
      sourceRoots(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("src/test")) {
      sourceRoots(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java"))
      sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources"))
    }
  }

  @Test
  fun `test content root merging for custom sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val moduleNode = createModuleNode().apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("testSrc/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("testSrc/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testSrc/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to moduleNode))

    assertContentRoots(moduleNode, projectPath.resolve("src"), projectPath.resolve("testSrc"))
    assertSourceRoots(moduleNode, projectPath.resolve("src")) {
      sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/java"))
      sourceRoots(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/resources"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("testSrc")) {
      sourceRoots(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc/java"))
      sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testSrc/resources"))
    }
  }

  @Test
  fun `test content root merging for incomplete sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    run {
      val moduleNode = createModuleNode().apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
        addChild(createSourceSetNode("test").apply {
          addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
            data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to moduleNode))

      assertContentRoots(moduleNode, projectPath.resolve("src/main"), projectPath.resolve("src/test"))
      assertSourceRoots(moduleNode, projectPath.resolve("src/main")) {
        sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java"))
      }
      assertSourceRoots(moduleNode, projectPath.resolve("src/test")) {
        sourceRoots(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java"))
      }
    }

    run {
      val moduleNode = createModuleNode().apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to moduleNode))

      assertContentRoots(moduleNode, projectPath.resolve("src/main"))
      assertSourceRoots(moduleNode, projectPath.resolve("src/main")) {
        sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java"))
      }
    }
  }

  @Test
  fun `test content root merging for flatten sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val moduleNode = createModuleNode().apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src").toString())
        })
        addChild(createContentRoot(projectPath.resolve("resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("testSrc")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc").toString())
        })
        addChild(createContentRoot(projectPath.resolve("testResources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testResources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to moduleNode))

    assertContentRoots(
      moduleNode,
      projectPath.resolve("src"), projectPath.resolve("resources"),
      projectPath.resolve("testSrc"), projectPath.resolve("testResources")
    )
    assertSourceRoots(moduleNode, projectPath.resolve("src")) {
      sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("src"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("resources")) {
      sourceRoots(ExternalSystemSourceType.RESOURCE, projectPath.resolve("resources"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("testSrc")) {
      sourceRoots(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("testResources")) {
      sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testResources"))
    }
  }

  @Test
  fun `test content root merging for generated sources`() {
    val projectPath = Path.of("path/to/project")
    val projectBuildPath = projectPath.resolve("build")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath, projectBuildPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val moduleNode = createModuleNode().apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
        addChild(createContentRoot(projectPath.resolve("build/generated/sources/annotationProcessor/java/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/main").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
        addChild(createContentRoot(projectPath.resolve("build/generated/sources/annotationProcessor/java/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/test").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to moduleNode))

    assertContentRoots(
      moduleNode,
      projectPath.resolve("src/main"), projectPath.resolve("src/test"),
      projectPath.resolve("build/generated/sources/annotationProcessor/java/main"),
      projectPath.resolve("build/generated/sources/annotationProcessor/java/test")
    )
    assertSourceRoots(moduleNode, projectPath.resolve("src/main")) {
      sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java"))
      sourceRoots(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("build/generated/sources/annotationProcessor/java/main")) {
      sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/main"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("src/test")) {
      sourceRoots(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java"))
      sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("build/generated/sources/annotationProcessor/java/test")) {
      sourceRoots(ExternalSystemSourceType.TEST_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/test"))
    }
  }

  @Test
  fun `test content root merging for external sources`() {
    val projectPath = Path.of("path/to/project")
    val externalPath = Path.of("path/to/external")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val moduleNode = createModuleNode().apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
        addChild(createContentRoot(externalPath.resolve("src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, externalPath.resolve("src/main/java").toString())
        })
        addChild(createContentRoot(externalPath.resolve("src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, externalPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
        addChild(createContentRoot(externalPath.resolve("src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, externalPath.resolve("src/test/java").toString())
        })
        addChild(createContentRoot(externalPath.resolve("src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, externalPath.resolve("src/test/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to moduleNode))

    assertContentRoots(
      moduleNode,
      projectPath.resolve("src/main"), projectPath.resolve("src/test"),
      externalPath.resolve("src/main/java"), externalPath.resolve("src/main/resources"),
      externalPath.resolve("src/test/java"), externalPath.resolve("src/test/resources"),
    )
    assertSourceRoots(moduleNode, projectPath.resolve("src/main")) {
      sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java"))
      sourceRoots(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("src/test")) {
      sourceRoots(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java"))
      sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources"))
    }
    assertSourceRoots(moduleNode, externalPath.resolve("src/main/java")) {
      sourceRoots(ExternalSystemSourceType.SOURCE, externalPath.resolve("src/main/java"))
    }
    assertSourceRoots(moduleNode, externalPath.resolve("src/main/resources")) {
      sourceRoots(ExternalSystemSourceType.RESOURCE, externalPath.resolve("src/main/resources"))
    }
    assertSourceRoots(moduleNode, externalPath.resolve("src/test/java")) {
      sourceRoots(ExternalSystemSourceType.TEST, externalPath.resolve("src/test/java"))
    }
    assertSourceRoots(moduleNode, externalPath.resolve("src/test/resources")) {
      sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, externalPath.resolve("src/test/resources"))
    }
  }

  @Test
  fun `test content root merging for sources in multi-module project`() {
    val projectPath = Path.of("path/to/project")
    val rootProjectModel = createProjectModel(projectPath, ":")
    val projectModel = createProjectModel(projectPath.resolve("module"), ":")
    val rootExternalProject = createExternalProject(projectPath)
    val externalProject = createExternalProject(projectPath.resolve("module"))
    val resolverContext = createResolveContext(
      rootProjectModel to rootExternalProject,
      projectModel to externalProject,
    )

    val rootModuleNode = createModuleNode().apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
      })
    }
    val moduleNode = createModuleNode().apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("module/src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("module/src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("module/src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("module/src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("module/src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("module/src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("module/src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("module/src/test/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(
      rootProjectModel to rootModuleNode,
      projectModel to moduleNode
    ))

    assertContentRoots(rootModuleNode, projectPath.resolve("src/main"), projectPath.resolve("src/test"))
    assertSourceRoots(rootModuleNode, projectPath.resolve("src/main")) {
      sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java"))
      sourceRoots(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources"))
    }
    assertSourceRoots(rootModuleNode, projectPath.resolve("src/test")) {
      sourceRoots(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java"))
      sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources"))
    }

    assertContentRoots(moduleNode, projectPath.resolve("module/src/main"), projectPath.resolve("module/src/test"))
    assertSourceRoots(moduleNode, projectPath.resolve("module/src/main")) {
      sourceRoots(ExternalSystemSourceType.SOURCE, projectPath.resolve("module/src/main/java"))
      sourceRoots(ExternalSystemSourceType.RESOURCE, projectPath.resolve("module/src/main/resources"))
    }
    assertSourceRoots(moduleNode, projectPath.resolve("module/src/test")) {
      sourceRoots(ExternalSystemSourceType.TEST, projectPath.resolve("module/src/test/java"))
      sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("module/src/test/resources"))
    }
  }
}