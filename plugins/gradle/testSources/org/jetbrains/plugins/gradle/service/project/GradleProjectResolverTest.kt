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

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
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

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for custom sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("testSrc")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testSrc/resources").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
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

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for incomplete sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    run {
      val expectedModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
        addChild(createSourceSetNode("test").apply {
          addChild(createContentRoot(projectPath.resolve("src/test")).apply {
            data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          })
        })
      }

      val actualModuleNode = createModuleNode("project").apply {
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

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

      assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
    }

    run {
      val expectedModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
      }

      val actualModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

      assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
    }
  }

  @Test
  fun `test content root merging for incomplete external sources`() {
    val rootPath = Path.of("path/to/root")
    val projectModel = createProjectModel(rootPath.resolve("projectRoot"), ":")
    val externalProject = createExternalProject(rootPath.resolve("projectRoot"))
    val resolverContext = createResolveContext(projectModel to externalProject)

    run {
      val expectedModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/main")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          })
        })
        addChild(createSourceSetNode("test").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/test")).apply {
            data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("externalRoot/src/test/java").toString())
          })
        })
      }

      val actualModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          })
        })
        addChild(createSourceSetNode("test").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/test/java")).apply {
            data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("externalRoot/src/test/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

      assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
    }

    run {
      val expectedModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/main")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          })
        })
      }

      val actualModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

      assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
    }
  }

  @Test
  fun `test content root merging for flatten sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
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

    val actualModuleNode = createModuleNode("project").apply {
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

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for generated sources`() {
    val projectPath = Path.of("path/to/project")
    val projectBuildPath = projectPath.resolve("build")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath, projectBuildPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
        addChild(createContentRoot(projectPath.resolve("build/generated/sources/annotationProcessor/java/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/main").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
        addChild(createContentRoot(projectPath.resolve("build/generated/sources/annotationProcessor/java/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/test").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
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

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for external sources`() {
    val rootPath = Path.of("path/to/root")
    val projectModel = createProjectModel(rootPath.resolve("projectRoot"), ":")
    val externalProject = createExternalProject(rootPath.resolve("projectRoot"))
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("projectRoot/src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("projectRoot/src/main/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("projectRoot/src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("projectRoot/src/test/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("externalRoot/src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("externalRoot/src/test/resources").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("projectRoot/src/main/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("projectRoot/src/main/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("projectRoot/src/test/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("projectRoot/src/test/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("externalRoot/src/test/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("externalRoot/src/test/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
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

    val expectedRootModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
      })
    }
    val expectedModuleNode = createModuleNode("module").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("module/src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("module/src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("module/src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("module/src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("module/src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("module/src/test/resources").toString())
        })
      })
    }

    val actualRootModuleNode = createModuleNode("project").apply {
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
    val actualModuleNode = createModuleNode("module").apply {
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
      rootProjectModel to actualRootModuleNode,
      projectModel to actualModuleNode
    ))

    assertModuleNodeEquals(expectedRootModuleNode, actualRootModuleNode)
    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }
}